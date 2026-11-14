package pt.isec.amov.cityfix.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.isec.amov.cityfix.data.MediaRepository
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.utils.CityFixUtils

/** Edição de perfil: nome, NIF e georreferenciação não são editáveis (enunciado, I.2.C). */
data class EditProfileUiState(
    val phone: String = "",
    val phoneError: String? = null,
    val impactDetectionEnabled: Boolean = false,
    val currentPhotoUrl: String? = null,
    val newPhotoUri: String? = null,
    val isSaving: Boolean = false
) {
    val isValid: Boolean get() = phoneError == null && phone.isNotBlank()
}

class EditProfileViewModel(
    private val cloudRepository: CityFixFirebaseRepository,
    private val mediaRepository: MediaRepository,
    private val user: CityFixUser
) : ViewModel() {
    private val _editState = MutableStateFlow(
        EditProfileUiState(
            phone = user.phone,
            impactDetectionEnabled = user.impactDetectionEnabled,
            currentPhotoUrl = user.photoUrl
        )
    )
    val editState: StateFlow<EditProfileUiState> = _editState.asStateFlow()

    fun onPhoneChanged(value: String) = _editState.update {
        it.copy(phone = value, phoneError = if (CityFixUtils.isValidPhone(value)) null else "Telefone inválido (9 dígitos)")
    }

    fun onImpactDetectionChanged(enabled: Boolean) = _editState.update { it.copy(impactDetectionEnabled = enabled) }

    fun onPhotoChanged(uri: Uri?) = _editState.update { it.copy(newPhotoUri = uri?.toString()) }

    fun save(onSuccess: (CityFixUser) -> Unit) {
        val current = _editState.value
        if (!current.isValid) return
        _editState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            val compressed = current.newPhotoUri?.let { mediaRepository.compressProfilePhoto(Uri.parse(it)) }
            val updated = user.copy(
                phone = current.phone.trim(),
                impactDetectionEnabled = current.impactDetectionEnabled,
                photoUrl = compressed?.toString() ?: user.photoUrl
            )
            cloudRepository.saveUser(updated)
            if (compressed != null) {
                cloudRepository.uploadMedia(
                    uri = compressed,
                    storagePath = "users/${user.id}/profile_${System.currentTimeMillis()}.jpg",
                    onSuccess = { url -> cloudRepository.saveUser(updated.copy(photoUrl = url)) },
                    onError = { } // mantém a cópia local comprimida
                )
            }
            _editState.update { it.copy(isSaving = false) }
            onSuccess(updated)
        }
    }
}
