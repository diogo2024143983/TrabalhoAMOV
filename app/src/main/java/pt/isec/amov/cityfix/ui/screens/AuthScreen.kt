package pt.isec.amov.cityfix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.components.LabeledSwitch
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.viewmodel.AuthFormUiState

/** Ecrã de login/registo sem estado: recebe o AuthFormUiState e emite eventos por lambdas. */
@Composable
fun AuthScreen(
    state: AuthFormUiState,
    onRegisterModeChange: (Boolean) -> Unit,
    onRoleSelected: (UserRole?) -> Unit,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onNifChange: (String) -> Unit,
    onGeoreferenceChange: (String) -> Unit,
    onImpactDetectionChange: (Boolean) -> Unit,
    onPhotoChange: (Uri?) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onGuest: () -> Unit,
    modifier: Modifier = Modifier,
    onForgotPassword: () -> Unit = {},
    onMfaCodeChange :(String) -> Unit = {},
    onVerifyMfa: () -> Unit = {},
    onCancelMfa: () -> Unit = {}
) {
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) onPhotoChange(uri)
        }
    if (state.mfaStep) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.mfa_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.mfa_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            ValidatedField(
                value = state.mfaCode,
                onValueChange = onMfaCodeChange,
                label = stringResource(R.string.mfa_code_label),
                error = state.mfaError,
                keyboardType = KeyboardType.Number
            )
            Button(
                onClick = onVerifyMfa,
                enabled = state.mfaCode.length == 6,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.mfa_confirm))
            }
            OutlinedButton(onClick = onCancelMfa, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_back))
            }
        }
        return
    }

    // Passo 1 do registo: escolher o tipo de conta
    if (state.isRegisterMode && state.role == null) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.auth_new_account), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.auth_choose_account_type),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RoleCard(
                icon = Icons.Default.Person,
                title = stringResource(R.string.auth_citizen_account),
                subtitle = stringResource(R.string.auth_citizen_account_desc),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onRoleSelected(UserRole.CITIZEN) }
            )
            RoleCard(
                icon = Icons.Default.Build,
                title = stringResource(R.string.role_operator),
                subtitle = stringResource(R.string.auth_operator_account_desc),
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onRoleSelected(UserRole.OPERATOR) }
            )
            TextButton(
                onClick = { onRegisterModeChange(false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.auth_have_account))
            }
        }
        return
    }

    val role = state.role ?: UserRole.CITIZEN
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = when {
                    !state.isRegisterMode -> stringResource(R.string.btn_login)
                    role == UserRole.CITIZEN -> stringResource(R.string.auth_register_citizen)
                    else -> stringResource(R.string.auth_register_operator)
                },
                style = MaterialTheme.typography.headlineSmall
            )
            if (state.isRegisterMode) {
                TextButton(onClick = { onRoleSelected(null) }) { Text(stringResource(R.string.auth_change_account_type)) }
            } else {
                Text(
                    stringResource(R.string.auth_login_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (state.isRegisterMode) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (state.photoUri != null) {
                    AsyncImage(
                        model = state.photoUri,
                        contentDescription = stringResource(R.string.cd_selected_photo),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = stringResource(R.string.cd_no_photo),
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            OutlinedButton(onClick = {
                photoPicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
            }) {
                Text(stringResource(if (state.photoUri != null) R.string.auth_change_photo else R.string.auth_add_photo))
            }
            ValidatedField(state.name, onNameChange, stringResource(R.string.name_label) + " *", state.nameError)
            ValidatedField(
                state.phone,
                onPhoneChange,
                stringResource(R.string.phone_label) + " *",
                state.phoneError,
                KeyboardType.Phone
            )
            ValidatedField(
                state.nif,
                onNifChange,
                stringResource(R.string.auth_nif_unique),
                state.nifError,
                KeyboardType.Number
            )
            if (role == UserRole.CITIZEN) {
                ValidatedField(
                    state.georeference,
                    onGeoreferenceChange,
                    stringResource(R.string.address_label) + " *",
                    state.georeferenceError
                )
                LabeledSwitch(
                    text = stringResource(R.string.impact_sensor_toggle),
                    checked = state.impactDetectionEnabled,
                    onCheckedChange = onImpactDetectionChange
                )
            }
        }

        ValidatedField(
            state.email, onEmailChange,
            if (role == UserRole.OPERATOR && state.isRegisterMode) stringResource(R.string.auth_municipal_email) else stringResource(R.string.email_label) + " *",
            state.emailError, KeyboardType.Email
        )
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text(stringResource(R.string.auth_password_min)) },
            isError = state.passwordError != null,
            supportingText = state.passwordError?.let { { Text(it) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        state.message?.let {
            Text(
                it,
                color = if (state.messageIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = onSubmit,
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.isRegisterMode) R.string.btn_register else R.string.auth_enter))
            }
        }
        TextButton(
            onClick = { onRegisterModeChange(!state.isRegisterMode) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(if (state.isRegisterMode) R.string.auth_have_account else R.string.auth_no_account))
        }
        if (!state.isRegisterMode) {
            TextButton(
                onClick = onForgotPassword,
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.forgot_password))
            }
            OutlinedButton(
                onClick = onGuest,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.btn_continue_as_guest)) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ValidatedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun RoleCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthScreenPreview() {
    CityFixTheme {
        AuthScreen(
            state = AuthFormUiState(email = "ana@", emailError = "Email inválido"),
            onRegisterModeChange = {},
            onRoleSelected = {},
            onNameChange = {},
            onPhoneChange = {},
            onNifChange = {},
            onGeoreferenceChange = {},
            onImpactDetectionChange = {},
            onPhotoChange = {},
            onEmailChange = {},
            onPasswordChange = {},
            onSubmit = {},
            onGuest = {}
        )
    }
}
