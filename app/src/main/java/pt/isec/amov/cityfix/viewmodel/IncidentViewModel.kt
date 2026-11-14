package pt.isec.amov.cityfix.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.isec.amov.cityfix.data.MediaRepository
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.data.notifications.TicketNotifier
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentList
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import java.util.UUID

data class IncidentUiState(
    val incidents: IncidentList = IncidentList(),
    val categories: List<IncidentCategory> = emptyList(),
    val selectedIncidentId: String? = null,
    val messages: List<IncidentMessage> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
) {
    val selectedIncident get() = selectedIncidentId?.let(incidents::findById)

    /** Categorias ativas definidas pela autarquia (ou as tipologias base, se ainda não existirem). */
    val activeCategories: List<IncidentCategory>
        get() = categories.filter { it.active }.ifEmpty { DEFAULT_CATEGORIES }

    companion object {
        val DEFAULT_CATEGORIES = IncidentList.sortCategories(
            listOf(
                IncidentCategory(id = "vias", name = "Vias Públicas"),
                IncidentCategory(id = "agua", name = "Água"),
                IncidentCategory(id = "iluminacao", name = "Iluminação"),
                IncidentCategory(id = "saneamento", name = "Saneamento"),
                IncidentCategory(id = "residuos", name = "Resíduos")
            )
        )
    }
}

class IncidentViewModel(
    private val cloudRepository: CityFixFirebaseRepository,
    private val localRepository: CityFixLocalRepository,
    private val notifier: TicketNotifier,
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private var ticketsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

    /** Tickets cujo fecho automático já foi tentado nesta sessão (evita repetir a transação). */
    private val closeAttempted = mutableSetOf<String>()

    private val _state = MutableStateFlow(IncidentUiState(incidents = IncidentList(localRepository.loadIncidents(emptyList()))))
    val state: StateFlow<IncidentUiState> = _state.asStateFlow()

    init {
        listenToTickets()
        listenToCategories()
    }

    /** Volta a subscrever (ex.: depois de iniciar sessão, as regras do Firestore passam a permitir a leitura). */
    fun refresh() {
        listenToTickets()
        listenToCategories()
    }

    private fun listenToTickets() {
        ticketsListener?.remove()
        ticketsListener = cloudRepository.observeTickets(
            onChanged = { tickets ->
                _state.update { it.copy(incidents = IncidentList(tickets), message = null) }

                val uid = cloudRepository.currentUserId
                val role = localRepository.loadUser()?.role ?: UserRole.ANONYMOUS
                notifier.notifyChanges(localRepository.loadIncidents(emptyList()), tickets, uid, role)
                notifier.notifyClosingSoon(tickets, uid)
                localRepository.saveIncidents(tickets)
                closeExpiredTickets()
            },
            onError = { e -> _state.update { it.copy(message = e.localizedMessage) } }
        )
    }

    private fun closeExpiredTickets() {
        if (cloudRepository.currentUserId == null) return
        val now = System.currentTimeMillis()
        val expired = _state.value.incidents.rawList().filter {
            it.status == IncidentStatus.RESOLVED && it.resolvedAt != null &&
                now - it.resolvedAt >= BusinessRules.REOPEN_WINDOW_MS && closeAttempted.add(it.id)
        }
        if (expired.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            expired.forEach { cloudRepository.closeIfExpiredBlocking(it.id, now) }
        }
    }

    private fun listenToCategories() {
        categoriesListener?.remove()
        categoriesListener = cloudRepository.observeCategories(
            onChanged = { cats -> _state.update { it.copy(categories = IncidentList.sortCategories(cats)) } }
        )
    }

    private fun actorOf(user: CityFixUser): CityFixUser = user.copy(id = cloudRepository.currentUserId ?: user.id)

    fun supportIncident(ticketId: String, user: CityFixUser) {
        cloudRepository.addSupporter(ticketId, cloudRepository.currentUserId ?: user.id)
    }

    // =========================================================================
    // DETALHE E MENSAGENS
    // =========================================================================

    fun selectIncident(ticketId: String) {
        if (_state.value.selectedIncidentId == ticketId) return
        messagesListener?.remove()
        messagesListener = cloudRepository.observeMessages(ticketId, onChanged = { msgs ->
            _state.update { it.copy(messages = msgs) }
        })
        _state.update { it.copy(selectedIncidentId = ticketId, messages = emptyList()) }
    }

    fun sendMessage(user: CityFixUser, text: String) {
        val incident = _state.value.selectedIncident ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank() || incident.status == IncidentStatus.CLOSED) return

        postMessage(user, incident.id, cleanText)

        // Resposta a um pedido de informações feito pela outra parte: o ticket volta ao estado anterior
        if (incident.status == IncidentStatus.INFORMATION_REQUEST &&
            incident.infoRequestedByRole.isNotBlank() &&
            incident.infoRequestedByRole != user.role.name
        ) {
            val previous = runCatching { IncidentStatus.valueOf(incident.statusBeforeInfoRequest) }.getOrDefault(IncidentStatus.OPEN)
            cloudRepository.changeStatus(
                ticketId = incident.id,
                newStatus = previous,
                actor = actorOf(user),
                note = "Esclarecimento prestado",
                extra = mapOf("infoRequestedByRole" to "", "statusBeforeInfoRequest" to "")
            )
        }
    }

    private fun postMessage(user: CityFixUser, ticketId: String, text: String) {
        val message = IncidentMessage(
            id = UUID.randomUUID().toString(),
            ticketId = ticketId,
            authorId = cloudRepository.currentUserId ?: user.id,
            authorName = user.name,
            authorRole = user.role,
            text = text
        )
        cloudRepository.sendMessage(message)
        cloudRepository.updateLastMessage(ticketId, message)
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

        postMessage(user, incident.id, clean)
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
            val prepared = mediaRepository.prepare(proofUri, "proof")
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
            mediaRepository.enqueueResolutionProof(incident.id, prepared)
            onComplete(true, null)
        }
    }

    fun reopenIncident(user: CityFixUser, reason: String, onResult: (Boolean, String?) -> Unit) {
        val incident = _state.value.selectedIncident
        when {
            incident == null -> onResult(false, "Ocorrência não encontrada.")
            !IncidentList.canReopen(incident) -> onResult(false, "Prazo de 5 dias expirado. A ocorrência encontra-se encerrada.")
            reason.isBlank() -> onResult(false, "É obrigatório justificar o motivo da reabertura.")
            else -> {
                cloudRepository.changeStatus(
                    ticketId = incident.id,
                    newStatus = IncidentStatus.OPEN,
                    actor = actorOf(user),
                    note = "Reaberto: ${reason.trim()}",
                    // Novo ciclo de resolução: o início dos trabalhos anterior deixa de se aplicar
                    extra = mapOf("reopenReason" to reason.trim(), "resolvedAt" to null, "workStartedAt" to null),
                    onError = { e -> onResult(false, e.localizedMessage) }
                )
                onResult(true, null)
            }
        }
    }

    override fun onCleared() {
        ticketsListener?.remove()
        categoriesListener?.remove()
        messagesListener?.remove()
        super.onCleared()
    }
}
