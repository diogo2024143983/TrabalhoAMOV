package pt.isec.amov.cityfix.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils
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

    private var ticketsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null

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
                localRepository.saveIncidents(tickets)
            },
            onError = { e -> _state.update { it.copy(message = e.localizedMessage) } }
        )
    }

    private fun listenToCategories() {
        categoriesListener?.remove()
        categoriesListener = cloudRepository.observeCategories(
            onChanged = { cats ->
                _state.update { it.copy(categories = cats) }
            }
        )
    }

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

        if (user.role == UserRole.ANONYMOUS) {
            if (!CityFixUtils.isWithinPracaRepublica(latitude, longitude)) {
                onComplete(
                    false,
                    "Utilizadores anónimos estão restritos a um raio de 10 km da Praça da República."
                )
                return
            }
        }

        // Verificar se já existe ocorrência ativa a menos de 10 metros com a mesma categoria e situação
        val duplicate = _state.value.incidents.firstOrNull { existing ->
            existing.status != IncidentStatus.CLOSED &&
                    existing.categoryId == categoryId &&
                    existing.situation == situation &&
                    CityFixUtils.distanceMeters(
                        existing.latitude,
                        existing.longitude,
                        latitude,
                        longitude
                    ) <= CityFixUtils.DUPLICATE_GROUP_RADIUS_METERS
        }

        if (duplicate != null) {
            val uid = cloudRepository.currentUserId ?: user.id
            cloudRepository.addSupporter(
                ticketId = duplicate.id,
                userId = uid,
                onSuccess = {
                    onComplete(
                        true,
                        "Já existia uma ocorrência igual a menos de 10m! Foste adicionado como apoiante."
                    )
                },
                onError = { e ->
                    onComplete(false, e.localizedMessage)
                }
            )
            return
        }

        _state.update { it.copy(loading = true, message = null) }
        val ticketId = UUID.randomUUID().toString()

        if (mediaUri != null) {
            val path = "tickets/$ticketId/media_${System.currentTimeMillis()}"
            cloudRepository.uploadMedia(
                uri = mediaUri,
                storagePath = path,
                onSuccess = { mediaUrl ->
                    saveTicketToFirestore(
                        user,
                        ticketId,
                        title,
                        description,
                        categoryId,
                        categoryName,
                        situation,
                        isPublic,
                        latitude,
                        longitude,
                        locationAddress,
                        mediaUrl,
                        onComplete
                    )
                },
                onError = { e ->
                    _state.update { it.copy(loading = false) }
                    onComplete(false, "Erro ao enviar foto/vídeo: ${e.localizedMessage}")
                }
            )
        } else {
            saveTicketToFirestore(
                user,
                ticketId,
                title,
                description,
                categoryId,
                categoryName,
                situation,
                isPublic,
                latitude,
                longitude,
                locationAddress,
                "",
                onComplete
            )
        }
    }

    private fun saveTicketToFirestore(
        user: CityFixUser,
        ticketId: String,
        title: String,
        description: String,
        categoryId: String,
        categoryName: String,
        situation: IncidentSituation,
        isPublic: Boolean,
        latitude: Double,
        longitude: Double,
        locationAddress: String,
        mediaUrl: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val currentUid = cloudRepository.currentUserId ?: user.id
        val incident = Incident(
            id = ticketId,
            title = title.trim(),
            description = description.trim(),
            categoryId = categoryId,
            categoryName = categoryName,
            situation = situation,
            status = IncidentStatus.OPEN,
            isPublic = isPublic,
            authorId = currentUid,
            authorName = if (user.role == UserRole.ANONYMOUS) "Cidadão Anónimo" else user.name,
            authorPhone = if (user.role == UserRole.ANONYMOUS) "" else user.phone,
            latitude = latitude,
            longitude = longitude,
            locationAddress = locationAddress.trim(),
            mediaUrl = mediaUrl,
            supporterIds = listOfNotNull(currentUid.takeIf { it.isNotBlank() })
        )

        cloudRepository.createTicket(
            incident = incident,
            onSuccess = {
                _state.update { it.copy(loading = false) }
                onComplete(true, "Ocorrência registada com sucesso!")
            },
            onError = { e ->
                _state.update { it.copy(loading = false) }
                onComplete(false, e.localizedMessage ?: "Erro ao gravar ocorrência.")
            }
        )
    }

    fun supportIncident(ticketId: String, userId: String) {
        val uid = cloudRepository.currentUserId ?: userId
        cloudRepository.addSupporter(ticketId, uid)
    }

    fun reopenIncident(
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

        cloudRepository.reopenTicket(
            ticketId = ticketId,
            reason = reason.trim(),
            onSuccess = { onResult(true, null) },
            onError = { e -> onResult(false, e.localizedMessage) }
        )
    }

    fun selectIncident(incident: Incident?) {
        messagesListener?.remove()
        messagesListener = incident?.let {
            cloudRepository.observeMessages(it.id, onChanged = { msgs ->
                _state.update { state -> state.copy(messages = msgs) }
            })
        }
        _state.update { it.copy(selectedIncident = incident, messages = emptyList()) }
    }

    fun sendMessage(user: CityFixUser, text: String) {
        val incident = _state.value.selectedIncident ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank()) return

        val message = IncidentMessage(
            id = UUID.randomUUID().toString(),
            ticketId = incident.id,
            authorId = cloudRepository.currentUserId ?: user.id,
            authorName = user.name,
            authorRole = user.role,
            text = cleanText
        )
        cloudRepository.sendMessage(message)
    }

    override fun onCleared() {
        ticketsListener?.remove()
        categoriesListener?.remove()
        messagesListener?.remove()
        super.onCleared()
    }
}