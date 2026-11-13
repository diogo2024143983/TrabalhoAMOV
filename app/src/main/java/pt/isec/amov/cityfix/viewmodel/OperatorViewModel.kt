package pt.isec.amov.cityfix.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.utils.CityFixUtils

    data class OperatorUiState(
    val pendingCitizens: List<CityFixUser> = emptyList(),
    val proximityAlertIncident: Incident? = null,
    val loading: Boolean = false
)

class OperatorViewModel(application: Application) : AndroidViewModel(application) {
    private val cloudRepository = CityFixFirebaseRepository(application)

    private val _state = MutableStateFlow(OperatorUiState())
    val state: StateFlow<OperatorUiState> = _state.asStateFlow()

    fun loadPendingCitizens() {
        _state.update { it.copy(loading = true) }
        cloudRepository.getPendingCitizens(
            onSuccess = { list ->
                _state.update { it.copy(pendingCitizens = list, loading = false) }
            },
            onError = {
                _state.update { it.copy(loading = false) }
            }
        )
    }

    fun validateCitizen(userId: String, active: Boolean) {
        cloudRepository.validateCitizen(
            userId = userId,
            active = active,
            onSuccess = { loadPendingCitizens() }
        )
    }

    fun saveCategory(name: String, active: Boolean) {
        if (name.isBlank()) return
        val category = IncidentCategory(name = name.trim(), active = active)
        cloudRepository.saveCategory(category)
    }

    fun checkOperatorProximity(incidents: List<Incident>, currentLat: Double, currentLon: Double) {
        val near = incidents.firstOrNull { ticket ->
            ticket.status in listOf(IncidentStatus.OPEN, IncidentStatus.IN_RESOLUTION) &&
                    CityFixUtils.distanceMeters(ticket.latitude, ticket.longitude, currentLat, currentLon) <= CityFixUtils.OPERATOR_PROXIMITY_RADIUS_METERS
        }
        _state.update { it.copy(proximityAlertIncident = near) }
    }

    fun dismissProximityAlert() {
        _state.update { it.copy(proximityAlertIncident = null) }
    }
}