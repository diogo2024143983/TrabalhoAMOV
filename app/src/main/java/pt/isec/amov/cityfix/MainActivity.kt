package pt.isec.amov.cityfix

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.screens.AuthScreen
import pt.isec.amov.cityfix.ui.screens.DashboardScreen
import pt.isec.amov.cityfix.ui.screens.IncidentDetailScreen
import pt.isec.amov.cityfix.ui.screens.IncidentListScreen
import pt.isec.amov.cityfix.ui.screens.NewIncidentScreen
import pt.isec.amov.cityfix.ui.screens.OperatorScreen
import pt.isec.amov.cityfix.ui.screens.ProfileScreen
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.viewmodel.AuthViewModel
import pt.isec.amov.cityfix.viewmodel.IncidentViewModel
import pt.isec.amov.cityfix.viewmodel.OperatorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CityFixTheme {
                CityFixApp()
            }
        }
    }
}

private enum class Screen { DASHBOARD, INCIDENTS, NEW, PROFILE, DETAIL, LOGIN, REGISTER, OPERATOR }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CityFixApp(
    authVm: AuthViewModel = viewModel(),
    incidentVm: IncidentViewModel = viewModel(),
    operatorVm: OperatorViewModel = viewModel()
) {
    val authState by authVm.state.collectAsStateWithLifecycle()
    val incidentState by incidentVm.state.collectAsStateWithLifecycle()
    val operatorState by operatorVm.state.collectAsStateWithLifecycle()

    var screen by remember { mutableStateOf(Screen.DASHBOARD) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    // Alerta de Proximidade do Operador (< 20m)
    operatorState.proximityAlertIncident?.let { nearTicket ->
        AlertDialog(
            onDismissRequest = { operatorVm.dismissProximityAlert() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Alerta de Proximidade (< 20m)") },
            text = { Text("Está a menos de 20 metros da ocorrência ativa: \"${nearTicket.title}\".") },
            confirmButton = {
                Button(onClick = {
                    incidentVm.selectIncident(nearTicket)
                    operatorVm.dismissProximityAlert()
                    screen = Screen.DETAIL
                }) {
                    Text("Ver Ocorrência")
                }
            },
            dismissButton = {
                TextButton(onClick = { operatorVm.dismissProximityAlert() }) {
                    Text("Ignorar")
                }
            }
        )
    }

    // Ecrãs de Login e Registo Separados
    if (screen == Screen.LOGIN || screen == Screen.REGISTER) {
        AuthScreen(
            startInRegisterMode = (screen == Screen.REGISTER),
            loading = authState.loading,
            message = authState.message,
            onBack = {
                authVm.clearMessage()
                screen = Screen.DASHBOARD
            },
            onLogin = { email, pass ->
                authVm.login(email, pass) { success, _ ->
                    if (success) screen = Screen.DASHBOARD
                }
            },
            onRegister = { name, email, pass, phone, nif, georef, role, impactEnabled, photoUri ->
                authVm.register(email, pass, name, phone, nif, georef, role, impactEnabled, photoUri) { success, _ ->
                    if (success) screen = Screen.DASHBOARD
                }
            },
            onGuest = {
                authVm.continueAsGuest { screen = Screen.DASHBOARD }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (screen == Screen.DETAIL) "Detalhes da Ocorrência" else "CityFix",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (screen != Screen.DETAIL) {
                            Text(
                                text = when (authState.user.role) {
                                    UserRole.ANONYMOUS -> "Cidadão Anónimo (Visualização)"
                                    UserRole.CITIZEN -> if (authState.user.active) "Munícipe Validado" else "Munícipe (Pendente de Validação)"
                                    UserRole.OPERATOR -> "Operador Municipal"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (screen == Screen.DETAIL) {
                        IconButton({ screen = Screen.INCIDENTS }) {
                            Icon(Icons.Default.ArrowBack, "Voltar")
                        }
                    }
                },
                actions = {
                    if (authState.user.role == UserRole.ANONYMOUS) {
                        IconButton({
                            authVm.clearMessage()
                            screen = Screen.LOGIN
                        }) {
                            Icon(Icons.Default.Login, "Iniciar sessão")
                        }
                    } else {
                        IconButton({
                            authVm.logout()
                            screen = Screen.DASHBOARD
                        }) {
                            Icon(Icons.Default.Logout, "Terminar sessão")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                NavigationBarItem(
                    selected = screen == Screen.DASHBOARD,
                    onClick = { screen = Screen.DASHBOARD },
                    icon = { Icon(Icons.Default.Home, "Início") },
                    label = { Text("Início") }
                )
                NavigationBarItem(
                    selected = screen == Screen.INCIDENTS,
                    onClick = { screen = Screen.INCIDENTS },
                    icon = { Icon(Icons.Default.List, "Ocorrências") },
                    label = { Text("Ocorrências") }
                )
                NavigationBarItem(
                    selected = screen == Screen.PROFILE,
                    onClick = { screen = Screen.PROFILE },
                    icon = { Icon(Icons.Default.Person, "Perfil") },
                    label = { Text("Perfil") }
                )
                if (authState.user.role == UserRole.OPERATOR) {
                    NavigationBarItem(
                        selected = screen == Screen.OPERATOR,
                        onClick = { screen = Screen.OPERATOR },
                        icon = { Icon(Icons.Default.Check, "Gestão") },
                        label = { Text("Gestão") }
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                Screen.DASHBOARD -> DashboardScreen(
                    incidents = incidentState.incidents,
                    user = authState.user,
                    onIncidents = { screen = Screen.INCIDENTS },
                    onNew = {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.ACCESS_COARSE_LOCATION)
                            add(Manifest.permission.CAMERA)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray()
                        permissionLauncher.launch(permissions)
                        screen = Screen.NEW
                    }
                )

                Screen.INCIDENTS -> IncidentListScreen(
                    incidents = incidentState.incidents.filter {
                        it.isPublic || it.authorId == authState.user.id || authState.user.role == UserRole.OPERATOR
                    },
                    onSelect = {
                        incidentVm.selectIncident(it)
                        screen = Screen.DETAIL
                    }
                )

                Screen.NEW -> NewIncidentScreen(
                    categories = incidentState.categories.filter { it.active },
                    onCancel = { screen = Screen.INCIDENTS },
                    onSubmit = { title, desc, catId, catName, sit, pub, lat, lon, addr, mediaUri ->
                        incidentVm.addIncident(
                            user = authState.user,
                            title = title,
                            description = desc,
                            categoryId = catId,
                            categoryName = catName,
                            situation = sit,
                            isPublic = pub,
                            latitude = lat,
                            longitude = lon,
                            locationAddress = addr,
                            mediaUri = mediaUri
                        ) { success, _ ->
                            if (success) screen = Screen.INCIDENTS
                        }
                    }
                )

                Screen.PROFILE -> ProfileScreen(
                    user = authState.user,
                    loading = authState.loading,
                    onUpdateProfile = authVm::updateUserProfile,
                    onLogin = {
                        authVm.clearMessage()
                        screen = Screen.LOGIN
                    },
                    onRegister = {
                        authVm.clearMessage()
                        screen = Screen.REGISTER
                    }
                )

                Screen.DETAIL -> incidentState.selectedIncident?.let { currentTicket ->
                    IncidentDetailScreen(
                        incident = currentTicket,
                        currentUser = authState.user,
                        messages = incidentState.messages,
                        onSupport = { incidentVm.supportIncident(currentTicket.id, authState.user.id) },
                        onUpdateStatus = { newStatus, proofUrl ->
                            operatorVm.updateTicketStatus(currentTicket.id, newStatus, proofUrl)
                        },
                        onReopen = { reason ->
                            incidentVm.reopenIncident(currentTicket.id, reason) { _, _ -> }
                        },
                        onSendMessage = { text ->
                            incidentVm.sendMessage(authState.user, text)
                        }
                    )
                }

                Screen.OPERATOR -> OperatorScreen(
                    incidents = incidentState.incidents.filter { it.status != IncidentStatus.CLOSED },
                    pendingCitizens = operatorState.pendingCitizens,
                    categories = incidentState.categories,
                    onRefreshPending = operatorVm::loadPendingCitizens,
                    onValidateCitizen = operatorVm::validateCitizen,
                    onSaveCategory = operatorVm::saveCategory,
                    onSelectIncident = {
                        incidentVm.selectIncident(it)
                        screen = Screen.DETAIL
                    }
                )

                Screen.LOGIN, Screen.REGISTER -> Unit
            }
        }
    }
}