package pt.isec.amov.cityfix

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import pt.isec.amov.cityfix.data.CityFixApp
import pt.isec.amov.cityfix.data.notifications.TicketNotifier
import pt.isec.amov.cityfix.data.work.TicketLifecycleWorker
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.services.ProximityService
import pt.isec.amov.cityfix.ui.navigation.AuthRoute
import pt.isec.amov.cityfix.ui.navigation.DashboardRoute
import pt.isec.amov.cityfix.ui.navigation.EditProfileRoute
import pt.isec.amov.cityfix.ui.navigation.IncidentDetailRoute
import pt.isec.amov.cityfix.ui.navigation.IncidentListRoute
import pt.isec.amov.cityfix.ui.navigation.OperatorRoute
import pt.isec.amov.cityfix.ui.navigation.ProfileRoute
import pt.isec.amov.cityfix.ui.navigation.ReportIncidentRoute
import pt.isec.amov.cityfix.ui.screens.AuthScreen
import pt.isec.amov.cityfix.ui.screens.DashboardScreen
import pt.isec.amov.cityfix.ui.screens.EditProfileScreen
import pt.isec.amov.cityfix.ui.screens.IncidentDetailScreen
import pt.isec.amov.cityfix.ui.screens.IncidentListScreen
import pt.isec.amov.cityfix.ui.screens.OperatorScreen
import pt.isec.amov.cityfix.ui.screens.ProfileScreen
import pt.isec.amov.cityfix.ui.screens.ReportIncidentScreen
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.utils.ImpactDetectionEffect
import pt.isec.amov.cityfix.utils.LocationTracker
import pt.isec.amov.cityfix.viewmodel.AuthFormViewModel
import pt.isec.amov.cityfix.viewmodel.AuthFormViewModelFactory
import pt.isec.amov.cityfix.viewmodel.AuthViewModel
import pt.isec.amov.cityfix.viewmodel.AuthViewModelFactory
import pt.isec.amov.cityfix.viewmodel.EditProfileViewModel
import pt.isec.amov.cityfix.viewmodel.EditProfileViewModelFactory
import pt.isec.amov.cityfix.viewmodel.IncidentViewModel
import pt.isec.amov.cityfix.viewmodel.IncidentViewModelFactory
import pt.isec.amov.cityfix.viewmodel.OperatorViewModel
import pt.isec.amov.cityfix.viewmodel.OperatorViewModelFactory
import pt.isec.amov.cityfix.viewmodel.ReportIncidentViewModel
import pt.isec.amov.cityfix.viewmodel.ReportIncidentViewModelFactory

class MainActivity : ComponentActivity() {
    /** Ticket a abrir quando a app é iniciada a partir de uma notificação. */
    private val pendingTicketId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTicketId.value = intent?.getStringExtra(TicketNotifier.EXTRA_TICKET_ID)
        setContent {
            CityFixTheme {
                CityFixRoot(
                    app = application as CityFixApp,
                    pendingTicketId = pendingTicketId.value,
                    onPendingTicketHandled = { pendingTicketId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(TicketNotifier.EXTRA_TICKET_ID)?.let { pendingTicketId.value = it }
    }
}

@Composable
private fun CityFixRoot(app: CityFixApp, pendingTicketId: String?, onPendingTicketHandled: () -> Unit) {
    val context = LocalContext.current
    val authVm: AuthViewModel = viewModel(factory = AuthViewModelFactory(app))
    val incidentVm: IncidentViewModel = viewModel(factory = IncidentViewModelFactory(app))
    val operatorVm: OperatorViewModel = viewModel(factory = OperatorViewModelFactory(app))

    val authState by authVm.state.collectAsStateWithLifecycle()
    val incidentState by incidentVm.state.collectAsStateWithLifecycle()
    val user = authState.user
    val navController = rememberNavController()

    // Permissões pedidas dinamicamente ao arrancar (padrão de P10)
    val permissionRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val locationGranted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted && user.role == UserRole.OPERATOR) ProximityService.start(context)
    }
    // Localização em segundo plano: necessária para a geofence do operador com a app fechada
    val backgroundLocationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Pedido faseado (aula de permissões): explicar primeiro porque é precisa a localização "sempre"
    var showBackgroundRationale by remember { mutableStateOf(false) }
    // Deteção de impactos: magnitude do último impacto por confirmar (null = sem diálogo)
    var pendingImpact by remember { mutableStateOf<Float?>(null) }
    var impactMagnitudeForReport by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        permissionRequest.launch(permissions.toTypedArray())
    }

    // Sempre que muda o utilizador: volta a subscrever os dados e liga/desliga a geofence do operador
    LaunchedEffect(user.id, user.role) {
        if (user.id.isNotBlank()) {
            incidentVm.refresh()
            TicketLifecycleWorker.runNow(context)
        }
        if (user.role == UserRole.OPERATOR && LocationTracker.hasPermission(context)) {
            ProximityService.start(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocation(context)) {
                showBackgroundRationale = true
            }
        } else {
            ProximityService.stop(context)
        }
    }

    // Abrir o ticket indicado numa notificação
    LaunchedEffect(pendingTicketId) {
        pendingTicketId?.let {
            navController.navigate(IncidentDetailRoute(it)) { launchSingleTop = true }
            onPendingTicketHandled()
        }
    }

    val showMessage: (String?) -> Unit = { msg -> if (!msg.isNullOrBlank()) Toast.makeText(context, msg, Toast.LENGTH_LONG).show() }
    val canReport = user.role == UserRole.OPERATOR || (user.role == UserRole.CITIZEN && user.active)
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // I.2.F: acelerómetro ativo só para munícipes validados que ativaram a opção no perfil
    ImpactDetectionEffect(
        enabled = user.role == UserRole.CITIZEN && user.active && user.impactDetectionEnabled
    ) { magnitude ->
        if (destination?.hasRoute<ReportIncidentRoute>() != true && pendingImpact == null) pendingImpact = magnitude
    }

    pendingImpact?.let { magnitude ->
        AlertDialog(
            onDismissRequest = { pendingImpact = null },
            title = { Text("Impacto detetado") },
            text = { Text("O telemóvel registou um impacto forte (%.1f m/s²). Pode ter passado por um buraco. Pretende reportar a ocorrência?".format(magnitude)) },
            confirmButton = {
                TextButton(onClick = {
                    impactMagnitudeForReport = magnitude
                    pendingImpact = null
                    navController.navigate(ReportIncidentRoute(fromImpact = true)) { launchSingleTop = true }
                }) { Text("Reportar") }
            },
            dismissButton = { TextButton(onClick = { pendingImpact = null }) { Text("Ignorar") } }
        )
    }

    if (showBackgroundRationale) {
        AlertDialog(
            onDismissRequest = { showBackgroundRationale = false },
            title = { Text("Localização em segundo plano") },
            text = { Text("Para avisar os munícipes quando está a menos de 20 m de uma ocorrência, mesmo com a app fechada, o CityFix precisa de acesso à localização \"Permitir sempre\". No ecrã seguinte escolha essa opção.") },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        backgroundLocationRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { showBackgroundRationale = false }) { Text("Agora não") } }
        )
    }

    Scaffold(
        topBar = {
            MainTopAppBar(
                navController = navController,
                destination = destination,
                user = user,
                onLogin = { navController.navigate(AuthRoute()) },
                onLogout = {
                    authVm.logout()
                    ProximityService.stop(context)
                    navController.navigate(DashboardRoute) { popUpTo(navController.graph.findStartDestination().id) { inclusive = true } }
                }
            )
        },
        bottomBar = {
            if (destination.isTopLevel()) MainBottomBar(navController, destination, isOperator = user.role == UserRole.OPERATOR)
        },
        floatingActionButton = {
            // Botão flutuante contextual: só nos ecrãs de listagem e para quem pode registar ocorrências
            if (canReport && (destination?.hasRoute<DashboardRoute>() == true || destination?.hasRoute<IncidentListRoute>() == true)) {
                FloatingActionButton(onClick = { navController.navigate(ReportIncidentRoute()) }) {
                    Icon(Icons.Default.Add, contentDescription = "Registar nova ocorrência")
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController = navController, startDestination = DashboardRoute, modifier = Modifier.padding(innerPadding)) {
            composable<DashboardRoute> {
                DashboardScreen(
                    user = user,
                    incidents = incidentState.incidents,
                    onIncidents = { navController.navigateTopLevel(IncidentListRoute) }
                )
            }
            composable<IncidentListRoute> {
                IncidentListScreen(
                    incidents = incidentState.incidents.visibleTo(user),
                    onIncidentClick = { id -> navController.navigate(IncidentDetailRoute(id)) }
                )
            }
            composable<IncidentDetailRoute> { backStack ->
                val route = backStack.toRoute<IncidentDetailRoute>()
                LaunchedEffect(route.incidentId) { incidentVm.selectIncident(route.incidentId) }
                IncidentDetailScreen(
                    incident = incidentState.incidents.findById(route.incidentId),
                    currentUser = user,
                    messages = if (incidentState.selectedIncidentId == route.incidentId) incidentState.messages else emptyList(),
                    loading = incidentState.loading,
                    onSupport = { incidentVm.supportIncident(route.incidentId, user) },
                    onChangeStatus = { status -> incidentVm.changeStatus(user, status) },
                    onRegisterWorkStart = { incidentVm.registerWorkStart(user) },
                    onRequestInfo = { text -> incidentVm.requestInformation(user, text) },
                    onResolveWithProof = { uri -> incidentVm.resolveWithProof(user, uri) { _, msg -> showMessage(msg) } },
                    onReopen = { reason -> incidentVm.reopenIncident(user, reason) { _, msg -> showMessage(msg) } },
                    onSendMessage = { text -> incidentVm.sendMessage(user, text) }
                )
            }
            composable<ReportIncidentRoute> { backStack ->
                // ViewModel com âmbito no ecrã (destruído ao sair do formulário)
                val reportVm: ReportIncidentViewModel = viewModel(viewModelStoreOwner = backStack, factory = ReportIncidentViewModelFactory(app))
                val formState by reportVm.formState.collectAsStateWithLifecycle()
                val route = backStack.toRoute<ReportIncidentRoute>()
                LaunchedEffect(route.fromImpact) {
                    if (route.fromImpact) {
                        reportVm.prefillFromImpact(incidentState.activeCategories, impactMagnitudeForReport)
                        // Localização do impacto = posição atual do municípe
                        LocationTracker.currentLocation(context)?.let {
                            reportVm.onLocationSelected(it.latitude, it.longitude, "Localização do impacto obtida por GPS.")
                        }
                    }
                }
                ReportIncidentScreen(
                    state = formState,
                    categories = incidentState.activeCategories,
                    onTitleChange = reportVm::onTitleChanged,
                    onDescriptionChange = reportVm::onDescriptionChanged,
                    onCategorySelected = reportVm::onCategorySelected,
                    onSituationSelected = reportVm::onSituationSelected,
                    onPublicChange = reportVm::onPublicChanged,
                    onAddressChange = reportVm::onAddressChanged,
                    onLocationSelected = reportVm::onLocationSelected,
                    onLocationMessage = reportVm::onLocationMessage,
                    onAttachmentsAdded = reportVm::onAttachmentsAdded,
                    onAttachmentRemoved = reportVm::onAttachmentRemoved,
                    onCreateCaptureUri = reportVm::createCaptureUri,
                    onSubmit = {
                        reportVm.submit(user, incidentState.incidents) { success, msg ->
                            showMessage(msg)
                            if (success) navController.popBackStack()
                        }
                    }
                )
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    user = user,
                    onLogin = { navController.navigate(AuthRoute()) },
                    onRegister = { navController.navigate(AuthRoute(startInRegisterMode = true)) },
                    onEditProfile = { navController.navigate(EditProfileRoute) }
                )
            }
            composable<EditProfileRoute> { backStack ->
                val editVm: EditProfileViewModel = viewModel(viewModelStoreOwner = backStack, factory = EditProfileViewModelFactory(app, user))
                val editState by editVm.editState.collectAsStateWithLifecycle()
                EditProfileScreen(
                    state = editState,
                    isCitizen = user.role == UserRole.CITIZEN,
                    onPhoneChange = editVm::onPhoneChanged,
                    onImpactDetectionChange = editVm::onImpactDetectionChanged,
                    onPhotoChange = editVm::onPhotoChanged,
                    onSave = {
                        editVm.save { updated ->
                            authVm.onUserUpdated(updated)
                            showMessage("Perfil atualizado.")
                            navController.popBackStack()
                        }
                    }
                )
            }
            composable<OperatorRoute> {
                val operatorState by operatorVm.state.collectAsStateWithLifecycle()
                OperatorScreen(
                    activeIncidents = incidentState.incidents.active(),
                    pendingCitizens = operatorState.pendingCitizens,
                    categories = incidentState.categories,
                    onRefreshPending = operatorVm::loadPendingCitizens,
                    onValidateCitizen = operatorVm::validateCitizen,
                    onAddCategory = operatorVm::addCategory,
                    onToggleCategory = operatorVm::toggleCategory,
                    onRenameCategory = operatorVm::renameCategory,
                    onIncidentClick = { id -> navController.navigate(IncidentDetailRoute(id)) }
                )
            }
            composable<AuthRoute> { backStack ->
                val route = backStack.toRoute<AuthRoute>()
                val formVm: AuthFormViewModel = viewModel(
                    viewModelStoreOwner = backStack,
                    factory = AuthFormViewModelFactory(app, route.startInRegisterMode)
                )
                val formState by formVm.formState.collectAsStateWithLifecycle()
                val onSignedIn: (String) -> Unit = { uid ->
                    authVm.onSignedIn(uid)
                    navController.navigate(DashboardRoute) { popUpTo(navController.graph.findStartDestination().id) { inclusive = true } }
                }
                AuthScreen(
                    state = formState,
                    onRegisterModeChange = formVm::onRegisterModeChanged,
                    onRoleSelected = formVm::onRoleSelected,
                    onNameChange = formVm::onNameChanged,
                    onPhoneChange = formVm::onPhoneChanged,
                    onNifChange = formVm::onNifChanged,
                    onGeoreferenceChange = formVm::onGeoreferenceChanged,
                    onImpactDetectionChange = formVm::onImpactDetectionChanged,
                    onPhotoChange = formVm::onPhotoChanged,
                    onEmailChange = formVm::onEmailChanged,
                    onPasswordChange = formVm::onPasswordChanged,
                    onSubmit = { if (formState.isRegisterMode) formVm.register(onSignedIn) else formVm.login(onSignedIn) },
                    onGuest = { authVm.continueAsGuest { navController.popBackStack() } },
                    onForgotPassword = formVm::resetPassword
                )
            }
        }
    }
}

/** TopAppBar contextual (MainTopAppBar de P07): título e ações dependem do destino atual. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainTopAppBar(
    navController: NavHostController,
    destination: NavDestination?,
    user: CityFixUser,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val title = when {
        destination?.hasRoute<IncidentDetailRoute>() == true -> "Detalhes da Ocorrência"
        destination?.hasRoute<ReportIncidentRoute>() == true -> "Nova Ocorrência"
        destination?.hasRoute<EditProfileRoute>() == true -> "Editar Perfil"
        destination?.hasRoute<AuthRoute>() == true -> "Autenticação"
        destination?.hasRoute<OperatorRoute>() == true -> "Gestão"
        else -> "CityFix"
    }
    TopAppBar(
        title = {
            androidx.compose.foundation.layout.Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (destination.isTopLevel()) {
                    Text(
                        text = when (user.role) {
                            UserRole.ANONYMOUS -> "Cidadão Anónimo (Visualização)"
                            UserRole.CITIZEN -> if (user.active) "Munícipe Validado" else "Munícipe (Pendente de Validação)"
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
            if (!destination.isTopLevel()) {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
            }
        },
        actions = {
            if (destination.isTopLevel()) {
                if (user.role == UserRole.ANONYMOUS) {
                    IconButton(onClick = onLogin) { Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Iniciar sessão") }
                } else {
                    IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Terminar sessão") }
                }
            }
        }
    )
}

@Composable
private fun MainBottomBar(navController: NavHostController, destination: NavDestination?, isOperator: Boolean) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
        BottomItem(navController, destination, DashboardRoute, Icons.Default.Home, "Início")
        BottomItem(navController, destination, IncidentListRoute, Icons.AutoMirrored.Filled.List, "Ocorrências")
        BottomItem(navController, destination, ProfileRoute, Icons.Default.Person, "Perfil")
        if (isOperator) BottomItem(navController, destination, OperatorRoute, Icons.Default.Check, "Gestão")
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomItem(
    navController: NavHostController,
    destination: NavDestination?,
    route: Any,
    icon: ImageVector,
    label: String
) {
    NavigationBarItem(
        selected = destination?.hierarchy?.any { it.hasRoute(route::class) } == true,
        onClick = { navController.navigateTopLevel(route) },
        icon = { Icon(icon, contentDescription = label) },
        label = { Text(label) }
    )
}

/** Navegação entre separadores: preserva o estado de cada um e evita duplicar destinos na pilha. */
private fun NavHostController.navigateTopLevel(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isTopLevel(): Boolean =
    this == null || hasRoute<DashboardRoute>() || hasRoute<IncidentListRoute>() || hasRoute<ProfileRoute>() || hasRoute<OperatorRoute>()

private fun hasBackgroundLocation(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
