package pt.isec.amov.cityfix.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.data.notifications.TicketNotifier
import pt.isec.amov.cityfix.data.work.MediaUploadWorker
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.StatusChange
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils
import pt.isec.amov.cityfix.utils.MediaUtils
import java.util.UUID

data class IncidentUiState(
    val incidents: List<Incident> = emptyList(),
    val categories: List<IncidentCategory> = emptyList(),
    val selectedIncident: Incident? = null,
    val messages: List<IncidentMessage> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

class IncidentViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepository = CityFixLocalRepository(application)
    private val cloudRepository = CityFixFirebaseRepository(application)
    private val notifier = TicketNotifier(application)

    private var ticketsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    /** Tickets cujo fecho automático já foi tentado nesta sessão (evita repetir a transação). */
    private val closeAttempted = mutableSetOf<String>()

    private val _state = MutableStateFlow(
        IncidentUiState(
            incidents = localRepository.loadIncidents(emptyList())
        )
    )
    val state: StateFlow<IncidentUiState> = _state.asStateFlow()

    init {
        listenToTickets()
        listenToCategories()
    }

    private fun listenToTickets() {
        ticketsListener?.remove()
        ticketsListener = cloudRepository.observeTickets(
            onChanged = { tickets ->
                val updatedSelected = _state.value.selectedIncident?.let { current ->
                    tickets.firstOrNull { it.id == current.id } ?: current
                }
                _state.update {
                    it.copy(
                        incidents = tickets,
                        selectedIncident = updatedSelected,
                        message = null
                    )
                }

                val uid = cloudRepository.currentUserId
                val role = localRepository.loadUser()?.role ?: UserRole.ANONYMOUS
                notifier.notifyChanges(localRepository.loadIncidents(emptyList()), tickets, uid, role)
                notifier.notifyClosingSoon(tickets, uid)
                localRepository.saveIncidents(tickets)
                closeExpiredTickets(tickets)
            },
            onError = { e -> _state.update { it.copy(message = e.localizedMessage) } }
        )
    }

    private fun closeExpiredTickets(tickets: List<Incident>) {
        if (cloudRepository.currentUserId == null) return
        val now = System.currentTimeMillis()
        val expired = tickets.filter {
            it.status == IncidentStatus.RESOLVED && it.resolvedAt != null &&
                now - it.resolvedAt >= CityFixUtils.REOPEN_WINDOW_MS && closeAttempted.add(it.id)
        }
        if (expired.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            expired.forEach { cloudRepository.closeIfExpiredBlocking(it.id, now) }
        }
    }

    private fun listenToCategories() {
        categoriesListener?.remove()
        categoriesListener = cloudRepository.observeCategories(
            onChanged = { cats ->
                _state.update { it.copy(categories = cats) }
            }
        )
    }

    private fun actorOf(user: CityFixUser): CityFixUser =
        user.copy(id = cloudRepository.currentUserId ?: user.id)

    // =========================================================================
    // ABERTURA DE OCORRÊNCIAS
    // =========================================================================

    fun addIncident(
        user: CityFixUser,
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        situation: IncidentSituation,
        isPublic: Boolean,
        latitude: Double,
        longitude: Double,
        locationAddress: String,
        mediaUri: Uri?,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (user.role == UserRole.CITIZEN && !user.active) {
            onComplete(false, "A tua conta aguarda validação do NIF por um operador municipal.")
            return
        }
        if (mediaUri == null) {
            onComplete(false, "É obrigatório anexar uma fotografia ou vídeo.")
            return
        }

        // Ocorrência de grupo: mesma categoria/situação a menos de 10 metros
        val duplicate = _state.value.incidents.firstOrNull { existing ->
            existing.status != IncidentStatus.CLOSED &&
                existing.categoryId == categoryId &&
                existing.situation == situation &&
                CityFixUtils.distanceMeters(existing.latitude, existing.longitude, latitude, longitude) <=
                CityFixUtils.DUPLICATE_GROUP_RADIUS_METERS
        }

        if (duplicate != null) {
            val uid = cloudRepository.currentUserId ?: user.id
            cloudRepository.addSupporter(
                ticketId = duplicate.id,
                userId = uid,
                onSuccess = {
                    onComplete(true, "Já existia uma ocorrência igual a menos de 10m! Foste adicionado como apoiante.")
                },
                onError = { e -> onComplete(false, e.localizedMessage) }
            )
            return
        }

        _state.update { it.copy(loading = true, message = null) }
        val ticketId = UUID.randomUUID().toString()

        viewModelScope.launch {
            // Compressão e geração da miniatura fora da main thread
            val prepared = withContext(Dispatchers.IO) {
                MediaUtils.prepare(getApplication(), mediaUri, "ticket")
            }
            if (prepared == null) {
                _state.update { it.copy(loading = false) }
                onComplete(false, "Não foi possível processar o ficheiro multimédia.")
                return@launch
            }

            val actor = actorOf(user)
            val now = System.currentTimeMillis()
            val incident = Incident(
                id = ticketId,
                title = title.trim(),
                description = description.trim(),
                categoryId = categoryId,
                categoryName = categoryName,
                situation = situation,
                status = IncidentStatus.OPEN,
                isPublic = isPublic,
                authorId = actor.id,
                authorName = user.name,
                authorPhone = user.phone,
                latitude = latitude,
                longitude = longitude,
                locationAddress = locationAddress.trim(),
                isVideo = prepared.isVideo,
                mediaPending = true,
                createdAt = now,
                history = listOf(
                    StatusChange(IncidentStatus.OPEN, now, actor.id, actor.name, actor.role, "Ocorrência submetida")
                ),
                supporterIds = listOfNotNull(actor.id.takeIf { it.isNotBlank() })
            )

            // A escrita fica em cache e sincroniza quando houver rede; o upload pesado fica no WorkManager.
            cloudRepository.createTicket(
                incident = incident,
                onError = { e -> _state.update { it.copy(message = e.localizedMessage) } }
            )
            MediaUploadWorker.enqueue(getApplication(), ticketId, MediaUploadWorker.KIND_MEDIA, prepared)

            _state.update { it.copy(loading = false) }
            onComplete(true, "Ocorrência registada! A multimédia será enviada em segundo plano.")
        }
    }

    fun supportIncident(ticketId: String, userId: String) {
        val uid = cloudRepository.currentUserId ?: userId
        cloudRepository.addSupporter(ticketId, uid)
    }

    // =========================================================================
    // CICLO DE VIDA DO TICKET
    // =========================================================================

    /** Transições simples feitas pelo operador (ex.: Aberto -> Em Resolução). */
    fun changeStatus(user: CityFixUser, newStatus: IncidentStatus, note: String = "") {
        val incident = _state.value.selectedIncident ?: return
        if (incident.status == IncidentStatus.CLOSED) return
        if (newStatus == IncidentStatus.RESOLVED || newStatus == IncidentStatus.CLOSED) return // usam fluxos próprios
        cloudRepository.changeStatus(incident.id, newStatus, actorOf(user), note) { e ->
            _state.update { it.copy(message = e.localizedMessage) }
        }
    }

    fun registerWorkStart(user: CityFixUser) {
        val incident = _state.value.selectedIncident ?: return
        if (incident.status != IncidentStatus.IN_RESOLUTION || incident.workStartedAt != null) return
        cloudRepository.registerWorkStart(incident.id, actorOf(user))
    }

    /**
     * Pedido de informações em qualquer dos sentidos: o operador pede dados ao munícipe,
     * ou o munícipe pede esclarecimentos (estimativa temporal/procedimento) à autarquia.
     */
    fun requestInformation(user: CityFixUser, text: String) {
        val incident = _state.value.selectedIncident ?: return
        val clean = text.trim()
        if (clean.isBlank() || incident.status in listOf(IncidentStatus.CLOSED, IncidentStatus.RESOLVED)) return

        postMessage(user, incident, clean)
        if (incident.status != IncidentStatus.INFORMATION_REQUEST) {
            cloudRepository.changeStatus(
                ticketId = incident.id,
                newStatus = IncidentStatus.INFORMATION_REQUEST,
                actor = actorOf(user),
                note = clean.take(120),
                extra = mapOf(
                    "infoRequestedByRole" to user.role.name,
                    "statusBeforeInfoRequest" to incident.status.name
                )
            )
        }
    }

    /** O operador declara o problema resolvido anexando foto/vídeo comprovativo. */
    fun resolveWithProof(user: CityFixUser, proofUri: Uri, onComplete: (Boolean, String?) -> Unit) {
        val incident = _state.value.selectedIncident ?: return
        if (user.role != UserRole.OPERATOR || incident.status != IncidentStatus.IN_RESOLUTION) {
            onComplete(false, "Só é possível resolver ocorrências em resolução.")
            return
        }
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val prepared = withContext(Dispatchers.IO) {
                MediaUtils.prepare(getApplication(), proofUri, "proof")
            }
            _state.update { it.copy(loading = false) }
            if (prepared == null) {
                onComplete(false, "Não foi possível processar a prova de resolução.")
                return@launch
            }
            cloudRepository.changeStatus(
                ticketId = incident.id,
                newStatus = IncidentStatus.RESOLVED,
                actor = actorOf(user),
                note = "Resolvido com prova ${if (prepared.isVideo) "em vídeo" else "fotográfica"}",
                extra = mapOf(
                    "proofPending" to true,
                    "resolutionProofIsVideo" to prepared.isVideo,
                    "reopenReason" to ""
                )
            )
            MediaUploadWorker.enqueue(getApplication(), incident.id, MediaUploadWorker.KIND_PROOF, prepared)
            onComplete(true, null)
        }
    }

    fun reopenIncident(
        user: CityFixUser,
        ticketId: String,
        reason: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        val incident = _state.value.incidents.firstOrNull { it.id == ticketId }
        if (incident == null) {
            onResult(false, "Ocorrência não encontrada.")
            return
        }
        if (!CityFixUtils.canReopenIncident(incident)) {
            onResult(false, "Prazo de 5 dias expirado. A ocorrência encontra-se encerrada.")
            return
        }
        if (reason.trim().isBlank()) {
            onResult(false, "É obrigatório justificar o motivo da reabertura.")
            return
        }

        cloudRepository.changeStatus(
            ticketId = ticketId,
            newStatus = IncidentStatus.OPEN,
            actor = actorOf(user),
            note = "Reaberto: ${reason.trim()}",
            extra = mapOf("reopenReason" to reason.trim(), "resolvedAt" to null),
            onError = { e -> onResult(false, e.localizedMessage) }
        )
        onResult(true, null)
    }

    // =========================================================================
    // MENSAGENS
    // =========================================================================

    fun selectIncident(incident: Incident?) {
        messagesListener?.remove()
        messagesListener = incident?.let {
            cloudRepository.observeMessages(it.id, onChanged = { msgs ->
                _state.update { state -> state.copy(messages = msgs) }
            })
        }
        _state.update { it.copy(selectedIncident = incident, messages = emptyList()) }
    }

    fun selectIncidentById(ticketId: String): Boolean {
        val incident = _state.value.incidents.firstOrNull { it.id == ticketId } ?: return false
        selectIncident(incident)
        return true
    }

    fun sendMessage(user: CityFixUser, text: String) {
        val incident = _state.value.selectedIncident ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank() || incident.status == IncidentStatus.CLOSED) return

        postMessage(user, incident, cleanText)

        // Resposta a um pedido de informações feito pela outra parte: o ticket volta ao estado anterior
        if (incident.status == IncidentStatus.INFORMATION_REQUEST &&
            incident.infoRequestedByRole.isNotBlank() &&
            incident.infoRequestedByRole != user.role.name
        ) {
            val previous = runCatching { IncidentStatus.valueOf(incident.statusBeforeInfoRequest) }
                .getOrDefault(IncidentStatus.OPEN)
            cloudRepository.changeStatus(
                ticketId = incident.id,
                newStatus = previous,
                actor = actorOf(user),
                note = "Esclarecimento prestado",
                extra = mapOf("infoRequestedByRole" to "", "statusBeforeInfoRequest" to "")
            )
        }
    }

    private fun postMessage(user: CityFixUser, incident: Incident, text: String) {
        val message = IncidentMessage(
            id = UUID.randomUUID().toString(),
            ticketId = incident.id,
            authorId = cloudRepository.currentUserId ?: user.id,
            authorName = user.name,
            authorRole = user.role,
            text = text
        )
        cloudRepository.sendMessage(message)
        cloudRepository.updateLastMessage(incident.id, message)
    }

    override fun onCleared() {
        ticketsListener?.remove()
        categoriesListener?.remove()
        messagesListener?.remove()
        super.onCleared()
    }
}
