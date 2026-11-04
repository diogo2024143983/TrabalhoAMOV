package pt.isec.amov.cityfix.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole

data class CityFixUiState(
    val user: CityFixUser = CityFixUser(name = "Anonymous citizen"),
    val incidents: List<Incident> = sampleIncidents,
    val selectedIncident: Incident? = null,
    val message: String? = null,
    val loading: Boolean = false
)

class CityFixViewModel : ViewModel() {
    private val _state = MutableStateFlow(CityFixUiState())
    val state: StateFlow<CityFixUiState> = _state.asStateFlow()

    fun signIn(name: String, email: String, operator: Boolean) {
        _state.update {
            it.copy(
                user = CityFixUser(
                    name = name.trim().ifBlank { "CityFix user" },
                    email = email.trim(),
                    role = if (operator) UserRole.OPERATOR else UserRole.CITIZEN,
                    active = operator
                ),
                message = null
            )
        }
    }

    fun continueAsGuest() {
        _state.update { it.copy(user = CityFixUser("Anonymous citizen"), message = null) }
    }

    fun signOut() = continueAsGuest()

    fun selectIncident(incident: Incident?) {
        _state.update { it.copy(selectedIncident = incident) }
    }

    fun addIncident(
        title: String,
        description: String,
        category: String,
        situation: IncidentSituation,
        public: Boolean,
        location: String
    ): Boolean {
        val duplicate = _state.value.incidents.any {
            it.category.equals(category, true) &&
                it.situation == situation &&
                it.location.equals(location, true)
        }
        val incident = Incident(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            category = category.trim(),
            situation = situation,
            public = public,
            author = _state.value.user.name,
            location = location.trim(),
            supporters = if (duplicate) 2 else 1
        )
        _state.update { it.copy(incidents = listOf(incident) + it.incidents) }
        return duplicate
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
    }
}

private val sampleIncidents = listOf(
    Incident("1", "Buraco na estrada", "Buraco junto à passadeira.", "Public roads", IncidentSituation.DAMAGE, author = "Municipality", location = "Praça da República", supporters = 8),
    Incident("2", "Candeeiro apagado", "A iluminação pública não funciona.", "Lighting", IncidentSituation.BREAKDOWN, author = "Municipality", location = "Rua da Sofia", supporters = 3),
    Incident("3", "Contentor danificado", "Contentor sem tampa.", "Waste collection", IncidentSituation.DESTRUCTION, author = "Ana", location = "Coimbra", public = false)
)
