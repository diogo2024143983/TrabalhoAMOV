package pt.isec.amov.cityfix.viewmodel

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.UserRole

data class AuthUiState(
    val user: CityFixUser = AuthViewModel.ANONYMOUS_USER,
    val loading: Boolean = false
)

/**
 * Sessão do utilizador (partilhada por toda a app). O preenchimento dos formulários de
 * login/registo vive no AuthFormViewModel, com âmbito no próprio ecrã.
 */
class AuthViewModel(
    private val cloudRepository: CityFixFirebaseRepository,
    private val localRepository: CityFixLocalRepository
) : ViewModel() {
    private var userListener: ListenerRegistration? = null

    private val _state = MutableStateFlow(AuthUiState(user = localRepository.loadUser() ?: ANONYMOUS_USER))
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        cloudRepository.currentUserId?.let(::observeUser)
    }

    /** Chamado pelo AuthFormViewModel depois de um login/registo com sucesso. */
    fun onSignedIn(uid: String) {
        cloudRepository.getUser(uid, onSuccess = { user ->
            if (user != null) setUser(user)
        })
        observeUser(uid)
        cloudRepository.registerCurrentMessagingToken()
    }

    fun onUserUpdated(user: CityFixUser) = setUser(user)

    private fun observeUser(uid: String) {
        userListener?.remove()
        userListener = cloudRepository.observeCurrentUser(uid, onChanged = { user ->
            if (user != null) {
                // Mantém a foto local caso o envio para o Storage ainda não tenha sido possível
                val currentPhoto = _state.value.user.photoUrl
                setUser(if (user.photoUrl.isNullOrBlank() && !currentPhoto.isNullOrBlank()) user.copy(photoUrl = currentPhoto) else user)
            }
        })
    }

    private fun setUser(user: CityFixUser) {
        _state.update { it.copy(user = user, loading = false) }
        localRepository.saveUser(user)
    }

    fun continueAsGuest(onResult: () -> Unit = {}) {
        _state.update { it.copy(loading = true) }
        cloudRepository.signInAnonymously(
            onSuccess = { uid ->
                setUser(ANONYMOUS_USER.copy(id = uid))
                onResult()
            },
            onError = {
                setUser(ANONYMOUS_USER)
                onResult()
            }
        )
    }

    fun logout() {
        userListener?.remove()
        cloudRepository.logout()
        localRepository.clearUser()
        _state.update { it.copy(user = ANONYMOUS_USER) }
    }

    override fun onCleared() {
        userListener?.remove()
        super.onCleared()
    }

    companion object {
        val ANONYMOUS_USER = CityFixUser(name = "Cidadão Anónimo", role = UserRole.ANONYMOUS, active = true)
    }
}
