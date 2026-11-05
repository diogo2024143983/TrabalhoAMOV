package pt.isec.amov.cityfix.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.google.firebase.firestore.ListenerRegistration
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.UserRole

data class CityFixUiState(
    val user: CityFixUser = CityFixUser(name = "Cidadão anónimo"),
    val incidents: List<Incident> = sampleIncidents,
    val selectedIncident: Incident? = null,
    val messages: List<IncidentMessage> = emptyList(),
    val message: String? = null,
    val loading: Boolean = false
)

class CityFixViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = CityFixLocalRepository(application)
    private val cloudRepository = CityFixFirebaseRepository(application)
    private var incidentsListener: ListenerRegistration? = null
    private var messagesListener: ListenerRegistration? = null
    private val _state = MutableStateFlow(
        CityFixUiState(
            user = repository.loadUser() ?: CityFixUser(name = "Cidadão anónimo"),
            incidents = repository.loadIncidents(sampleIncidents)
        )
    )
    val state: StateFlow<CityFixUiState> = _state.asStateFlow()

    init {
        incidentsListener = cloudRepository.observeIncidents(
            onChanged = { incidents ->
                _state.update { it.copy(incidents = incidents, message = null) }
                repository.saveIncidents(incidents)
            },
            onError = ::showCloudError
        )
    }

    fun signIn(
        name: String,
        email: String,
        password: String,
        onComplete: (Boolean) -> Unit
    ) {
        _state.update { it.copy(loading = true, message = null) }
        cloudRepository.signInOrRegister(
            email = email.trim(),
            password = password,
            onSuccess = {
                updateSignedInUser(name, email)
                _state.update { it.copy(loading = false) }
                onComplete(true)
            },
            onError = {
                showCloudError(it)
                _state.update { state -> state.copy(loading = false) }
                onComplete(false)
            }
        )
    }

    private fun updateSignedInUser(name: String, email: String) {
        _state.update {
            it.copy(
                user = CityFixUser(
                    name = name.trim().ifBlank { "Utilizador CityFix" },
                    email = email.trim(),
                    role = UserRole.CITIZEN,
                    active = true
                ),
                message = null
            )
        }
        repository.saveUser(_state.value.user)
        cloudRepository.saveUser(_state.value.user, ::showCloudError)
        cloudRepository.registerCurrentMessagingToken(::showCloudError)
    }

    fun continueAsGuest() {
        _state.update { it.copy(user = CityFixUser("Cidadão anónimo"), message = null) }
        repository.clearUser()
    }

    fun signOut() = continueAsGuest()

    fun selectIncident(incident: Incident?) {
        messagesListener?.remove()
        messagesListener = incident?.let {
            cloudRepository.observeMessages(it.id, { messages ->
                _state.update { state -> state.copy(messages = messages) }
            }, ::showCloudError)
        }
        _state.update { it.copy(selectedIncident = incident, messages = emptyList()) }
    }

    fun sendMessage(text: String) {
        val incident = _state.value.selectedIncident ?: return
        val cleanText = text.trim()
        if (cleanText.isBlank()) return
        val message = IncidentMessage(
            id = UUID.randomUUID().toString(),
            incidentId = incident.id,
            authorId = cloudRepository.currentUserId.orEmpty(),
            authorName = _state.value.user.name,
            text = cleanText
        )
        cloudRepository.sendMessage(message, ::showCloudError)
    }

    fun addIncident(
        title: String,
        description: String,
        category: String,
        situation: IncidentSituation,
        public: Boolean,
        location: String,
        mediaUri: Uri?,
        onComplete: (Boolean) -> Unit
    ) {
        if (mediaUri == null) {
            _state.update { it.copy(message = "Seleciona uma fotografia ou vídeo antes de submeter.") }
            onComplete(false)
            return
        }
        val duplicate = _state.value.incidents.any {
            it.category.equals(category, true) &&
                it.situation == situation &&
                it.location.equals(location, true)
        }
        val incidentId = UUID.randomUUID().toString()
        val incident = Incident(
            id = incidentId,
            title = title.trim(),
            description = description.trim(),
            category = category.trim(),
            situation = situation,
            public = public,
            author = _state.value.user.name,
            location = location.trim(),
            ownerId = cloudRepository.currentUserId.orEmpty(),
            supporters = if (duplicate) 2 else 1,
            hasMedia = false
        )
        _state.update { it.copy(loading = true, message = null) }
        cloudRepository.uploadMedia(
            uri = mediaUri,
            incidentId = incidentId,
            onSuccess = { mediaUrl ->
                val uploaded = incident.copy(hasMedia = true, mediaUrl = mediaUrl)
                _state.update { it.copy(incidents = listOf(uploaded) + it.incidents, loading = false) }
                repository.saveIncidents(_state.value.incidents)
                cloudRepository.saveIncidents(_state.value.incidents, ::showCloudError)
                onComplete(duplicate)
            },
            onError = {
                showCloudError(it)
                _state.update { state -> state.copy(loading = false) }
                onComplete(false)
            }
        )
    }

    fun advanceStatus(incident: Incident) {
        val next = when (incident.status) {
            IncidentStatus.OPEN -> IncidentStatus.INFORMATION_REQUEST
            IncidentStatus.INFORMATION_REQUEST -> IncidentStatus.IN_RESOLUTION
            IncidentStatus.IN_RESOLUTION -> IncidentStatus.RESOLVED
            IncidentStatus.RESOLVED -> IncidentStatus.CLOSED
            IncidentStatus.CLOSED -> IncidentStatus.CLOSED
        }
        _state.update { current ->
            current.copy(
                incidents = current.incidents.map { if (it.id == incident.id) it.copy(status = next) else it },
                selectedIncident = incident.copy(status = next)
            )
        }
        repository.saveIncidents(_state.value.incidents)
        cloudRepository.saveIncidents(_state.value.incidents, ::showCloudError)
    }

    override fun onCleared() {
        incidentsListener?.remove()
        messagesListener?.remove()
        super.onCleared()
    }

    private fun showCloudError(error: Exception) {
        _state.update { it.copy(message = "Sincronização cloud indisponível: ${error.localizedMessage}") }
    }
}

private val sampleIncidents = listOf(
    Incident("1", "Buraco na estrada", "Buraco junto à passadeira.", "Estradas públicas", IncidentSituation.DAMAGE, author = "Município", location = "Praça da República", supporters = 8),
    Incident("2", "Candeeiro apagado", "A iluminação pública não funciona.", "Iluminação", IncidentSituation.BREAKDOWN, author = "Município", location = "Rua da Sofia", supporters = 3),
    Incident("3", "Contentor danificado", "Contentor sem tampa.", "Recolha de resíduos", IncidentSituation.DESTRUCTION, author = "Ana", location = "Coimbra", public = false)
)
