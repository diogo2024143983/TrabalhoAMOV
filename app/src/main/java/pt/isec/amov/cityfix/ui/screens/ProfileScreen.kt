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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.UserRole
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
        Text("Perfil do Utilizador", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        ProfileAvatar(user.photoUrl)

        if (user.role == UserRole.ANONYMOUS) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Estás a navegar como Cidadão Anónimo.", fontWeight = FontWeight.SemiBold)
                    Text("Inicia sessão ou cria uma nova conta para reportar e acompanhar ocorrências.")
                }
            }
            Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("Iniciar Sessão") }
            OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("Criar Nova Conta") }
        } else {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nome: ${user.name}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    HorizontalDivider()
                    Text("Email: ${user.email}")
                    Text("Telefone: ${user.phone.ifBlank { "Não indicado" }}")
                    Text("NIF: ${user.nif}")
                    if (user.role == UserRole.CITIZEN) {
                        Text("Morada: ${user.georeference.ifBlank { "Não indicada" }}")
                        Text("Deteção de Impactos: ${if (user.impactDetectionEnabled) "Ativa" else "Desativada"}")
                    }
                    Text("Função: ${roleLabel(user.role)}")
                    Text(
                        text = "Estado: ${if (user.active) "Ativo" else "Pendente de Validação por Operador"}",
                        color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Button(onClick = onEditProfile, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Settings, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Editar Perfil")
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
        Text("Editar Perfil", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
        Text(
            "Nome, NIF e morada não podem ser alterados.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        ProfileAvatar(state.newPhotoUri ?: state.currentPhotoUrl)
        OutlinedButton(onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text("Trocar Fotografia")
        }
        OutlinedTextField(
            value = state.phone,
            onValueChange = onPhoneChange,
            label = { Text("Número de Telefone") },
            isError = state.phoneError != null,
            supportingText = state.phoneError?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        if (isCitizen) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.impactDetectionEnabled, onCheckedChange = onImpactDetectionChange)
                Spacer(Modifier.size(8.dp))
                Text("Deteção Automática de Impactos")
            }
        }
        if (state.isSaving) {
            CircularProgressIndicator()
        } else {
            Button(onClick = onSave, enabled = state.isValid, modifier = Modifier.fillMaxWidth()) { Text("Guardar Alterações") }
        }
    }
}

@Composable
private fun ProfileAvatar(photo: String?) {
    Surface(modifier = Modifier.size(110.dp).clip(CircleShape), color = MaterialTheme.colorScheme.primaryContainer) {
        if (!photo.isNullOrBlank()) {
            AsyncImage(model = photo, contentDescription = "Foto de perfil", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Default.Person, contentDescription = "Sem foto", modifier = Modifier.padding(24.dp).fillMaxSize(), tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
