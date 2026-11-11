package pt.isec.amov.cityfix.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.model.UserRole

@Composable
fun AuthScreen(
    startInRegisterMode: Boolean = false,
    loading: Boolean,
    message: String?,
    onBack: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String, String, String, String, UserRole, Boolean, Uri?) -> Unit,
    onGuest: () -> Unit
) {
    var isRegisterMode by remember(startInRegisterMode) { mutableStateOf(startInRegisterMode) }
    // No registo, começa a null para perguntar primeiro qual o tipo de conta!
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var nif by remember { mutableStateOf("") }
    var georef by remember { mutableStateOf("") }
    var impactEnabled by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) selectedPhotoUri = uri
    }

    // PASSO 1 DO REGISTO: Escolher primeiro se é Munícipe ou Operador Municipal
    if (isRegisterMode && selectedRole == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Criar Nova Conta", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Selecione primeiro o tipo de conta que pretende criar:",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Cartão 1: Munícipe
            Card(
                onClick = { selectedRole = UserRole.CITIZEN },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Conta de Munícipe", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Para cidadãos residentes reportarem e acompanharem ocorrências no município.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Cartão 2: Operador Municipal
            Card(
                onClick = { selectedRole = UserRole.OPERATOR },
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Operador Municipal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Para funcionários dos serviços camarários (requer email institucional @cm-coimbra.pt).",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            TextButton(
                onClick = { isRegisterMode = false },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Já tens conta? Inicia sessão")
            }

            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar")
            }
        }
        return
    }

    val role = selectedRole ?: UserRole.CITIZEN

    // PASSO 2 DO REGISTO (OU LOGIN): Preencher os dados
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (!isRegisterMode) "Iniciar Sessão"
                    else if (role == UserRole.CITIZEN) "Registo de Munícipe"
                    else "Registo de Operador Municipal",
                    style = MaterialTheme.typography.headlineSmall
                )
                if (isRegisterMode) {
                    TextButton(onClick = { selectedRole = null }) {
                        Text("← Mudar tipo de conta")
                    }
                } else {
                    Text(
                        text = "Introduza o seu email e palavra-passe.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (isRegisterMode) {
            // Fotografia de Perfil Opcional no Registo
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        if (selectedPhotoUri != null) {
                            AsyncImage(
                                model = selectedPhotoUri,
                                contentDescription = "Foto selecionada",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = "Sem foto",
                                modifier = Modifier.padding(20.dp).fillMaxSize(),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(onClick = { photoPicker.launch("image/*") }) {
                        Text(if (selectedPhotoUri != null) "Trocar Foto Escolhida" else "Adicionar Foto (Opcional)")
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome Completo *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Número de Telefone *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = nif,
                    onValueChange = { nif = it },
                    label = { Text("NIF (Único) *") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (role == UserRole.CITIZEN) {
                item {
                    OutlinedTextField(
                        value = georef,
                        onValueChange = { georef = it },
                        label = { Text("Morada / Georreferenciação *") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = impactEnabled,
                            onClick = { impactEnabled = !impactEnabled },
                            label = { Text("Ativar Deteção Automática de Impactos") }
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = {
                    Text(
                        if (role == UserRole.OPERATOR && isRegisterMode)
                            "Email Municipal (@cm-coimbra.pt) *"
                        else
                            "Email *"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Palavra-passe (mín. 6 carateres) *") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!message.isNullOrBlank()) {
            item {
                Text(message, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            if (loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (isRegisterMode) {
                            onRegister(name, email, password, phone, nif, georef, role, impactEnabled, selectedPhotoUri)
                        } else {
                            onLogin(email, password)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRegisterMode) "Criar Conta" else "Entrar")
                }
            }
        }

        item {
            TextButton(
                onClick = {
                    isRegisterMode = !isRegisterMode
                    selectedRole = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isRegisterMode) "Já tens conta? Inicia sessão" else "Não tens conta? Criar nova conta")
            }
        }

        item {
            OutlinedButton(onClick = onGuest, modifier = Modifier.fillMaxWidth()) {
                Text("Continuar como Cidadão Anónimo")
            }
        }

        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar ao Início")
            }
        }
    }
}