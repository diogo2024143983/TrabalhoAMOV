package pt.isec.amov.cityfix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.UserRole

@Composable
fun ProfileScreen(
    user: CityFixUser,
    loading: Boolean,
    onUpdateProfile: (String, Boolean, Uri?, (Boolean, String?) -> Unit) -> Unit,
    onLogin: () -> Unit,
    onRegister: () -> Unit
) {
    // Controla se estamos na vista normal do Perfil ou nas Definições de Conta
    var isEditingSettings by remember { mutableStateOf(false) }

    var phone by remember(user.phone) { mutableStateOf(user.phone) }
    var impactEnabled by remember(user.impactDetectionEnabled) { mutableStateOf(user.impactDetectionEnabled) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var feedbackMessage by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedPhotoUri = uri
    }

    // =========================================================================
    // ECRÃ DE DEFINIÇÕES DE CONTA (EDITAR PERFIL E FOTO)
    // =========================================================================
    if (isEditingSettings && user.role != UserRole.ANONYMOUS) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Text(
                    text = "Definições de Conta",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Altere a sua fotografia, telefone ou preferências. (Nome, NIF e Morada não podem ser alterados).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Mudar ou Adicionar Fotografia
            item {
                val imageModel: Any? = selectedPhotoUri ?: user.photoUrl?.takeIf { it.isNotBlank() }
                Surface(
                    modifier = Modifier.size(110.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    if (imageModel != null) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Foto de perfil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Sem foto",
                            modifier = Modifier.padding(24.dp).fillMaxSize(),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { photoPicker.launch("image/*") }) {
                    val hasPhoto = selectedPhotoUri != null || !user.photoUrl.isNullOrBlank()
                    Text(if (hasPhoto) "Trocar Fotografia" else "Adicionar Fotografia")
                }
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de Telefone") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (user.role == UserRole.CITIZEN) {
                item {
                    FilterChip(
                        selected = impactEnabled,
                        onClick = { impactEnabled = !impactEnabled },
                        label = { Text("Deteção Automática de Impactos: ${if (impactEnabled) "Ativa" else "Desativada"}") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = feedbackMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            item {
                if (loading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            onUpdateProfile(phone, impactEnabled, selectedPhotoUri) { success, msg ->
                                feedbackMessage = msg
                                if (success) {
                                    selectedPhotoUri = null
                                    isEditingSettings = false
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Guardar Alterações")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            selectedPhotoUri = null
                            phone = user.phone
                            impactEnabled = user.impactDetectionEnabled
                            isEditingSettings = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancelar e Voltar ao Perfil")
                    }
                }
            }
        }
        return
    }

    // =========================================================================
    // ECRÃ PRINCIPAL DE PERFIL (VISUALIZAÇÃO LIMPA)
    // =========================================================================
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text(
                text = "Perfil do Utilizador",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Fotografia de Perfil
        item {
            val imageModel: Any? = user.photoUrl?.takeIf { it.isNotBlank() }
            Surface(
                modifier = Modifier.size(110.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (imageModel != null) {
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "Foto de perfil",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Sem foto",
                        modifier = Modifier.padding(24.dp).fillMaxSize(),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (user.role == UserRole.ANONYMOUS) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Estás a navegar como Cidadão Anónimo.", fontWeight = FontWeight.SemiBold)
                        Text("Inicia sessão ou cria uma nova conta para reportar e acompanhar ocorrências.")
                    }
                }
            }
            item {
                Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                    Text("Iniciar Sessão")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) {
                    Text("Criar Nova Conta")
                }
            }
        } else {
            // Cartão Limpo com os Dados do Utilizador
            item {
                val roleLabel = when (user.role) {
                    UserRole.ANONYMOUS -> "Cidadão Anónimo"
                    UserRole.CITIZEN -> "Munícipe"
                    UserRole.OPERATOR -> "Operador Municipal"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
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
                        Text("Função: $roleLabel")
                        Text(
                            text = "Estado: ${if (user.active) "Ativo" else "Pendente de Validação por Operador"}",
                            color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                item {
                    Text(
                        text = feedbackMessage!!,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Botão para abrir as Definições de Conta
            item {
                Button(
                    onClick = {
                        feedbackMessage = null
                        isEditingSettings = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Definições de Conta / Editar Perfil")
                }
            }
        }
    }
}