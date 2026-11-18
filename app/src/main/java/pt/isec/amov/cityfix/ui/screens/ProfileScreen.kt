package pt.isec.amov.cityfix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.components.LabeledSwitch
import pt.isec.amov.cityfix.ui.components.roleLabel
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.viewmodel.EditProfileUiState

/** Perfil (apenas visualização, sem estado). */
@Composable
fun ProfileScreen(
    user: CityFixUser,
    onLogin: () -> Unit,
    onRegister: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        ProfileAvatar(user.photoUrl)

        if (user.role == UserRole.ANONYMOUS) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.profile_anonymous_title), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.profile_anonymous_message))
                }
            }
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_login)) }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auth_new_account)) }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val notGiven = stringResource(R.string.profile_not_given)
                    Text(stringResource(R.string.profile_name, user.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    Text(stringResource(R.string.profile_email, user.email))
                    Text(stringResource(R.string.profile_phone, user.phone.ifBlank { notGiven }))
                    Text(stringResource(R.string.profile_nif, user.nif))
                    if (user.role == UserRole.CITIZEN) {
                        Text(stringResource(R.string.profile_address, user.georeference.ifBlank { notGiven }))
                        Text(stringResource(R.string.profile_impact, stringResource(if (user.impactDetectionEnabled) R.string.profile_enabled else R.string.profile_disabled)))
                    }
                    Text(stringResource(R.string.profile_role, roleLabel(user.role)))
                    Text(
                        text = stringResource(R.string.profile_state, stringResource(if (user.active) R.string.status_active else R.string.status_pending_validation)),
                        color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.profile_edit))
            }
        }
    }
}

/** Edição de perfil sem estado, alimentada pelo EditProfileUiState. */
@Composable
fun EditProfileScreen(
    state: EditProfileUiState,
    isCitizen: Boolean,
    onPhoneChange: (String) -> Unit,
    onImpactDetectionChange: (Boolean) -> Unit,
    onPhotoChange: (Uri?) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPhotoChange(uri)
    }
    Column(
        modifier = modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(R.string.profile_edit), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        Text(
            stringResource(R.string.profile_edit_locked),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        ProfileAvatar(state.newPhotoUri ?: state.currentPhotoUrl)
        OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text(stringResource(R.string.auth_change_photo))
        }
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text(stringResource(R.string.phone_label)) },
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        if (isCitizen) {
            LabeledSwitch(
                text = stringResource(R.string.impact_sensor_toggle),
                checked = state.impactDetectionEnabled,
                onCheckedChange = onImpactDetectionChange
            )
        }
        if (state.isSaving) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onSave, enabled = state.isValid, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.btn_save_changes)) }
        }
    }
}

@Composable
private fun ProfileAvatar(photo: String?) {
    Surface(modifier = Modifier.size(110.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
        if (!photo.isNullOrBlank()) {
            AsyncImage(model = photo, contentDescription = stringResource(R.string.cd_profile_photo), modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, contentDescription = stringResource(R.string.cd_no_photo), modifier = Modifier.padding(24.dp).fillMaxSize(), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    CityFixTheme {
        ProfileScreen(
            user = CityFixUser(name = "Ana Martins", email = "ana@isec.pt", phone = "912345678", nif = "123456789", role = UserRole.CITIZEN),
            onLogin = {}, onRegister = {}, onEditProfile = {}
        )
    }
}
