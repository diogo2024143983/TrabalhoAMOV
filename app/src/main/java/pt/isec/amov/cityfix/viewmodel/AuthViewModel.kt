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
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils

data class AuthUiState(
    val user: CityFixUser = CityFixUser(name = "Cidadão Anónimo", role = UserRole.ANONYMOUS, active = true),
    val loading: Boolean = false,
    val message: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val localRepository = CityFixLocalRepository(application)
    private val cloudRepository = CityFixFirebaseRepository(application)
    private var userListener: ListenerRegistration? = null

    private val _state = MutableStateFlow(
        AuthUiState(
            user = localRepository.loadUser() ?: CityFixUser(name = "Cidadão Anónimo", role = UserRole.ANONYMOUS, active = true)
        )
    )
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        cloudRepository.currentUserId?.let { uid ->
            observeUser(uid)
        }
    }

    private fun observeUser(uid: String) {
        userListener?.remove()
        userListener = cloudRepository.observeCurrentUser(uid, onChanged = { user ->
            if (user != null) {
                // Preserva a foto caso a versão local já tenha uma foto guardada
                val currentPhoto = _state.value.user.photoUrl
                val mergedUser = if (user.photoUrl.isNullOrBlank() && !currentPhoto.isNullOrBlank()) {
                    user.copy(photoUrl = currentPhoto)
                } else {
                    user
                }
                _state.update { it.copy(user = mergedUser) }
                localRepository.saveUser(mergedUser)
            }
        })
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun login(
        email: String,
        pass: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (email.isBlank() || pass.isBlank()) {
            val msg = "Por favor preenche o email e a palavra-passe."
            _state.update { it.copy(message = msg) }
            onResult(false, msg)
            return
        }

        _state.update { it.copy(loading = true, message = null) }

        cloudRepository.login(
            email = email.trim(),
            pass = pass,
            onSuccess = { uid ->
                cloudRepository.getUser(uid, onSuccess = { user ->
                    _state.update { it.copy(loading = false) }
                    if (user != null) {
                        _state.update { it.copy(user = user) }
                        localRepository.saveUser(user)
                        observeUser(uid)
                    }
                    onResult(true, null)
                }, onError = {
                    _state.update { it.copy(loading = false) }
                    onResult(true, null)
                })
            },
            onError = { e ->
                val msg = e.localizedMessage ?: "Email ou palavra-passe incorretos."
                _state.update { it.copy(loading = false, message = msg) }
                onResult(false, msg)
            }
        )
    }

    fun register(
        email: String,
        pass: String,
        name: String,
        phone: String,
        nif: String,
        georeference: String,
        role: UserRole,
        impactDetectionEnabled: Boolean,
        photoUri: Uri? = null,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (name.isBlank() || email.isBlank() || pass.isBlank() || phone.isBlank() || nif.isBlank()) {
            val msg = "Todos os campos com * são obrigatórios."
            _state.update { it.copy(message = msg) }
            onResult(false, msg)
            return
        }

        if (!CityFixUtils.isValidPortugueseNif(nif)) {
            val msg = "O NIF introduzido não é válido."
            _state.update { it.copy(message = msg) }
            onResult(false, msg)
            return
        }

        if (!CityFixUtils.isValidPhone(phone)) {
            val msg = "O número de telefone não é válido."
            _state.update { it.copy(message = msg) }
            onResult(false, msg)
            return
        }

        if (role == UserRole.OPERATOR && !CityFixUtils.isValidMunicipalEmail(email)) {
            val msg = "O email de operador deve pertencer ao município (ex: @cm-coimbra.pt)."
            _state.update { it.copy(message = msg) }
            onResult(false, msg)
            return
        }

        _state.update { it.copy(loading = true, message = null) }

        // Comprime e guarda uma cópia local permanente da foto se existir
        val compressedUri = photoUri?.let {
            CityFixUtils.compressAndSaveImage(getApplication(), it, "profile") ?: it
        }

        cloudRepository.isNifAlreadyRegistered(nif) { exists ->
            if (exists) {
                val msg = "Este NIF já se encontra associado a outra conta."
                _state.update { it.copy(loading = false, message = msg) }
                onResult(false, msg)
                return@isNifAlreadyRegistered
            }

            val isActive = (role == UserRole.OPERATOR)
            val newUser = CityFixUser(
                name = name.trim(),
                email = email.trim(),
                phone = phone.trim(),
                nif = nif.trim(),
                role = role,
                active = isActive,
                georeference = georeference.trim(),
                photoUrl = compressedUri?.toString(),
                impactDetectionEnabled = impactDetectionEnabled
            )

            cloudRepository.register(
                email = email.trim(),
                pass = pass,
                user = newUser,
                onSuccess = { uid ->
                    if (compressedUri != null) {
                        val path = "users/$uid/profile_${System.currentTimeMillis()}.jpg"
                        cloudRepository.uploadMedia(
                            uri = compressedUri,
                            storagePath = path,
                            onSuccess = { downloadUrl ->
                                val userWithCloudPhoto = newUser.copy(id = uid, photoUrl = downloadUrl)
                                cloudRepository.saveUser(userWithCloudPhoto)
                                _state.update { it.copy(user = userWithCloudPhoto, loading = false) }
                                localRepository.saveUser(userWithCloudPhoto)
                                observeUser(uid)
                                onResult(true, null)
                            },
                            onError = {
                                // Se o Storage falhar, mantém a foto comprimida localmente!
                                val userWithLocalPhoto = newUser.copy(id = uid, photoUrl = compressedUri.toString())
                                cloudRepository.saveUser(userWithLocalPhoto)
                                _state.update { it.copy(user = userWithLocalPhoto, loading = false) }
                                localRepository.saveUser(userWithLocalPhoto)
                                observeUser(uid)
                                onResult(true, null)
                            }
                        )
                    } else {
                        val userWithId = newUser.copy(id = uid)
                        _state.update { it.copy(user = userWithId, loading = false) }
                        localRepository.saveUser(userWithId)
                        observeUser(uid)
                        onResult(true, null)
                    }
                },
                onError = { e ->
                    val msg = e.localizedMessage ?: "Erro ao criar conta."
                    _state.update { it.copy(loading = false, message = msg) }
                    onResult(false, msg)
                }
            )
        }
    }

    fun continueAsGuest(onResult: () -> Unit = {}) {
        _state.update { it.copy(loading = true, message = null) }
        cloudRepository.signInAnonymously(
            onSuccess = { uid ->
                val anonUser = CityFixUser(id = uid, name = "Cidadão Anónimo", role = UserRole.ANONYMOUS, active = true)
                _state.update { it.copy(user = anonUser, loading = false) }
                localRepository.saveUser(anonUser)
                onResult()
            },
            onError = {
                val anonUser = CityFixUser(name = "Cidadão Anónimo", role = UserRole.ANONYMOUS, active = true)
                _state.update { it.copy(user = anonUser, loading = false) }
                localRepository.saveUser(anonUser)
                onResult()
            }
        )
    }

    fun logout() {
        userListener?.remove()
        cloudRepository.logout()
        localRepository.clearUser()
        val anon = CityFixUser(name = "Cidadão Anónimo", role = UserRole.ANONYMOUS, active = true)
        _state.update { it.copy(user = anon, message = null) }
    }

    fun updateUserProfile(
        phone: String,
        impactDetectionEnabled: Boolean,
        newPhotoUri: Uri?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val currentUser = _state.value.user
        if (!CityFixUtils.isValidPhone(phone)) {
            onResult(false, "O número de telefone não é válido.")
            return
        }

        _state.update { it.copy(loading = true, message = null) }

        val compressedUri = newPhotoUri?.let {
            CityFixUtils.compressAndSaveImage(getApplication(), it, "profile") ?: it
        }

        if (compressedUri != null) {
            val path = "users/${currentUser.id}/profile_${System.currentTimeMillis()}.jpg"
            cloudRepository.uploadMedia(
                uri = compressedUri,
                storagePath = path,
                onSuccess = { downloadUrl ->
                    val updated = currentUser.copy(
                        phone = phone.trim(),
                        impactDetectionEnabled = impactDetectionEnabled,
                        photoUrl = downloadUrl
                    )
                    cloudRepository.saveUser(updated)
                    _state.update { it.copy(user = updated, loading = false) }
                    localRepository.saveUser(updated)
                    onResult(true, "Definições e fotografia guardadas com sucesso!")
                },
                onError = {
                    // Mantém a foto comprimida localmente mesmo se o Firebase Storage não estiver ativo
                    val updated = currentUser.copy(
                        phone = phone.trim(),
                        impactDetectionEnabled = impactDetectionEnabled,
                        photoUrl = compressedUri.toString()
                    )
                    cloudRepository.saveUser(updated)
                    _state.update { it.copy(user = updated, loading = false) }
                    localRepository.saveUser(updated)
                    onResult(true, "Definições e fotografia guardadas com sucesso!")
                }
            )
        } else {
            val updated = currentUser.copy(
                phone = phone.trim(),
                impactDetectionEnabled = impactDetectionEnabled
            )
            cloudRepository.saveUser(
                user = updated,
                onSuccess = {
                    _state.update { it.copy(user = updated, loading = false) }
                    localRepository.saveUser(updated)
                    onResult(true, "Definições de conta atualizadas com sucesso!")
                },
                onError = { e ->
                    _state.update { it.copy(loading = false) }
                    onResult(false, e.localizedMessage ?: "Erro ao atualizar perfil.")
                }
            )
        }
    }

    override fun onCleared() {
        userListener?.remove()
        super.onCleared()
    }
}