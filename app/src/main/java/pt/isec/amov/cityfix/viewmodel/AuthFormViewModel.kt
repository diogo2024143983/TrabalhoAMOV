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
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils
import pt.isec.amov.cityfix.data.notifications.TicketNotifier

/** Estado imutável do formulário de login/registo, com validação por campo (padrão UiState de P08). */
data class AuthFormUiState(
    val isRegisterMode: Boolean = false,
    val role: UserRole? = null,
    val name: String = "",
    val phone: String = "",
    val nif: String = "",
    val georeference: String = "",
    val impactDetectionEnabled: Boolean = false,
    val photoUri: String? = null,
    val email: String = "",
    val password: String = "",
    val nameError: String? = null,
    val phoneError: String? = null,
    val nifError: String? = null,
    val georeferenceError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    /** false quando [message] é informativa (ex.: email de recuperação enviado). */
    val messageIsError: Boolean = true,
    val mfaStep: Boolean = false,
    val mfaCode: String = "",
    val mfaError: String? = null

) {
    val isValid: Boolean
        get() {
            val credentialsOk = emailError == null && passwordError == null && email.isNotBlank() && password.length >= 6
            if (!isRegisterMode) return credentialsOk
            val common = credentialsOk && nameError == null && phoneError == null && nifError == null &&
                name.isNotBlank() && phone.isNotBlank() && nif.isNotBlank()
            return if (role == UserRole.CITIZEN) common && georeferenceError == null && georeference.isNotBlank() else common
        }
}

class AuthFormViewModel(
    private val cloudRepository: CityFixFirebaseRepository,
    private val mediaRepository: MediaRepository,
    startInRegisterMode: Boolean,
    private val ticketNotifier: TicketNotifier? = null
) : ViewModel() {
    private val _formState = MutableStateFlow(AuthFormUiState(isRegisterMode = startInRegisterMode))
    val formState: StateFlow<AuthFormUiState> = _formState.asStateFlow()
    private var expectedMfaCode: String? = null
    private var pendingUid: String? = null

    fun onRegisterModeChanged(register: Boolean) =
        _formState.update { AuthFormUiState(isRegisterMode = register, email = it.email) }

    fun onRoleSelected(role: UserRole?) = _formState.update {
        it.copy(role = role, emailError = role?.let { r -> emailErrorFor(it.email, r, true) })
    }

    fun onNameChanged(value: String) = _formState.update {
        it.copy(name = value, nameError = if (value.isBlank()) "O nome é obrigatório" else null)
    }

    fun onPhoneChanged(value: String) = _formState.update {
        it.copy(phone = value, phoneError = if (CityFixUtils.isValidPhone(value)) null else "Telefone inválido (9 dígitos)")
    }

    fun onNifChanged(value: String) = _formState.update {
        it.copy(nif = value, nifError = if (CityFixUtils.isValidPortugueseNif(value)) null else "NIF inválido")
    }

    fun onGeoreferenceChanged(value: String) = _formState.update {
        it.copy(georeference = value, georeferenceError = if (value.isBlank()) "A morada é obrigatória" else null)
    }

    fun onImpactDetectionChanged(enabled: Boolean) = _formState.update { it.copy(impactDetectionEnabled = enabled) }

    fun onPhotoChanged(uri: Uri?) = _formState.update { it.copy(photoUri = uri?.toString()) }

    fun onEmailChanged(value: String) = _formState.update {
        it.copy(email = value, emailError = emailErrorFor(value, it.role, it.isRegisterMode))
    }

    fun onPasswordChanged(value: String) = _formState.update {
        it.copy(password = value, passwordError = if (value.length >= 6) null else "Mínimo de 6 carateres")
    }

    private fun emailErrorFor(email: String, role: UserRole?, register: Boolean): String? = when {
        !CityFixUtils.isValidEmail(email) -> "Email inválido"
        register && role == UserRole.OPERATOR && !CityFixUtils.isValidMunicipalEmail(email) ->
            "O email de operador deve pertencer ao município (ex: @cm-coimbra.pt)"
        else -> null
    }

    fun login(onSuccess: (String) -> Unit) {
        val form = _formState.value
        if (!form.isValid) return
        _formState.update { it.copy(isLoading = true, message = null, messageIsError = true) }
        cloudRepository.login(
            email = form.email.trim(),
            pass = form.password,
            onSuccess = { uid ->
                val code = (100000..999999).random().toString()
                expectedMfaCode = code
                pendingUid = uid
                ticketNotifier?.notifyMfaCode(code)
                _formState.update {
                    it.copy(
                        isLoading = false,
                        mfaStep = true,
                        mfaCode = "",
                        mfaError = null,
                        messageIsError = false,
                        message = "Código de 2.º fator (MFA) enviado por notificação ($code)."
                    )
                }
            },
            onError = { e ->
                _formState.update { it.copy(isLoading = false, message = e.localizedMessage ?: "Email ou palavra-passe incorretos.") }
            }
        )
    }

    fun onMfaCodeChanged(value: String) = _formState.update {
        it.copy(mfaCode = value.filter(Char::isDigit).take(6), mfaError = null)
    }

    fun verifyMfa(onSuccess: (String) -> Unit) {
        val uid = pendingUid ?: return
        if (_formState.value.mfaCode == expectedMfaCode) {
            expectedMfaCode = null
            pendingUid = null
            _formState.update { it.copy(mfaStep = false, message = null) }
            onSuccess(uid)
        } else {
            _formState.update { it.copy(mfaError = "Código de verificação incorreto.") }
        }
    }

    fun cancelMfa() {
        expectedMfaCode = null
        pendingUid = null
        cloudRepository.logout()
        _formState.update { it.copy(mfaStep = false, mfaCode = "", mfaError = null, message = null) }
    }

    /** Recuperação de credenciais: o Firebase envia um email com a ligação para redefinir a palavra-passe. */
    fun resetPassword() {
        val email = _formState.value.email.trim()
        if (!CityFixUtils.isValidEmail(email)) {
            _formState.update { it.copy(emailError = "Indique o email da conta para recuperar a palavra-passe") }
            return
        }
        _formState.update { it.copy(isLoading = true, message = null) }
        cloudRepository.sendPasswordReset(
            email = email,
            onSuccess = {
                _formState.update { it.copy(isLoading = false, messageIsError = false, message = "Enviámos um email para $email com instruções para redefinir a palavra-passe.") }
            },
            onError = { e ->
                _formState.update { it.copy(isLoading = false, messageIsError = true, message = e.localizedMessage ?: "Não foi possível enviar o email de recuperação.") }
            }
        )
    }

    fun register(onSuccess: (String) -> Unit) {
        val form = _formState.value
        val role = form.role ?: return
        if (!form.isValid) return
        _formState.update { it.copy(isLoading = true, message = null) }

        viewModelScope.launch {
            // Compressão local da fotografia antes do envio
            val compressedPhoto = form.photoUri?.let { mediaRepository.compressProfilePhoto(Uri.parse(it)) }

            cloudRepository.isNifAlreadyRegistered(form.nif.trim()) { exists ->
                if (exists) {
                    _formState.update { it.copy(isLoading = false, nifError = "Este NIF já se encontra associado a outra conta.") }
                    return@isNifAlreadyRegistered
                }
                val newUser = CityFixUser(
                    name = form.name.trim(),
                    email = form.email.trim(),
                    phone = form.phone.trim(),
                    nif = form.nif.trim(),
                    role = role,
                    active = role == UserRole.OPERATOR, // munícipe fica pendente até validação do NIF
                    georeference = form.georeference.trim(),
                    photoUrl = compressedPhoto?.toString(),
                    impactDetectionEnabled = form.impactDetectionEnabled
                )
                cloudRepository.register(
                    email = form.email.trim(),
                    pass = form.password,
                    user = newUser,
                    onSuccess = { uid ->
                        if (compressedPhoto == null) {
                            _formState.update { it.copy(isLoading = false) }
                            onSuccess(uid)
                        } else {
                            uploadProfilePhoto(uid, newUser.copy(id = uid), compressedPhoto) { onSuccess(uid) }
                        }
                    },
                    onError = { e ->
                        _formState.update { it.copy(isLoading = false, message = e.localizedMessage ?: "Erro ao criar conta.") }
                    }
                )
            }
        }
    }

    private fun uploadProfilePhoto(uid: String, user: CityFixUser, photo: Uri, onDone: () -> Unit) {
        cloudRepository.uploadMedia(
            uri = photo,
            storagePath = "users/$uid/profile_${System.currentTimeMillis()}.jpg",
            onSuccess = { url -> cloudRepository.saveUser(user.copy(photoUrl = url)) },
            // Se o Storage falhar, a fotografia comprimida fica guardada localmente
            onError = { }
        )
        _formState.update { it.copy(isLoading = false) }
        onDone()
    }
}
