package pt.isec.amov.cityfix.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.IncidentCategory

data class OperatorUiState(
    val pendingCitizens: List<CityFixUser> = emptyList(),
    val loading: Boolean = false
)

class OperatorViewModel(private val cloudRepository: CityFixFirebaseRepository) : ViewModel() {
    private val _state = MutableStateFlow(OperatorUiState())
    val state: StateFlow<OperatorUiState> = _state.asStateFlow()

    fun loadPendingCitizens() {
        _state.update { it.copy(loading = true) }
        cloudRepository.getPendingCitizens(
            onSuccess = { list -> _state.update { it.copy(pendingCitizens = list, loading = false) } },
            onError = { _state.update { it.copy(loading = false) } }
        )
    }

    fun validateCitizen(userId: String, active: Boolean) {
        cloudRepository.validateCitizen(userId = userId, active = active, onSuccess = { loadPendingCitizens() })
    }

    // Tipologias de problemas: criar, ativar/desativar e editar (enunciado, I.3.E)

    fun addCategory(name: String) {
        if (name.isBlank()) return
        cloudRepository.saveCategory(IncidentCategory(name = name.trim(), active = true))
    }

    fun toggleCategory(category: IncidentCategory) {
        cloudRepository.saveCategory(category.copy(active = !category.active))
    }

    fun renameCategory(category: IncidentCategory, newName: String) {
        if (newName.isBlank()) return
        cloudRepository.saveCategory(category.copy(name = newName.trim()))
    }
}
