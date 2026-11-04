package pt.isec.amov.cityfix

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.content.pm.PackageManager
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.viewmodel.CityFixViewModel
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CityFixTheme { CityFixApp() } }
    }
}

private enum class Screen { DASHBOARD, INCIDENTS, NEW, PROFILE, DETAIL, LOGIN, OPERATOR }

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CityFixApp(vm: CityFixViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.DASHBOARD) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    if (screen == Screen.LOGIN) {
        LoginScreen(onBack = { screen = Screen.DASHBOARD }, onLogin = { n, e, p ->
            vm.signIn(n, e, p) { success ->
                if (success) screen = Screen.DASHBOARD
            }
        }, onGuest = { vm.continueAsGuest(); screen = Screen.DASHBOARD })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (screen == Screen.DETAIL) "Incident details" else "CityFix", fontWeight = FontWeight.SemiBold)
                        if (screen != Screen.DETAIL) Text(
                            if (state.user.role == UserRole.ANONYMOUS) "Neighbourhood reports" else "Your community, together",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (screen == Screen.DETAIL) IconButton({ screen = Screen.INCIDENTS }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (state.user.role == UserRole.ANONYMOUS) IconButton({ screen = Screen.LOGIN }) {
                        Icon(Icons.Default.Login, "Sign in")
                    } else IconButton({ vm.signOut(); screen = Screen.DASHBOARD }) {
                        Icon(Icons.Default.Logout, "Sign out")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                NavigationBarItem(screen == Screen.DASHBOARD, { screen = Screen.DASHBOARD }, { Icon(Icons.Default.Home, "Dashboard") }, label = { Text("Dashboard") })
                NavigationBarItem(screen == Screen.INCIDENTS, { screen = Screen.INCIDENTS }, { Icon(Icons.Default.List, "Incidents") }, label = { Text("Incidents") })
                NavigationBarItem(screen == Screen.PROFILE, { screen = Screen.PROFILE }, { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile") })
                if (state.user.role == UserRole.OPERATOR) NavigationBarItem(screen == Screen.OPERATOR, { screen = Screen.OPERATOR }, { Icon(Icons.Default.Check, "Management") }, label = { Text("Manage") })
            }
        },
    ) { padding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (screen) {
                Screen.DASHBOARD -> DashboardScreen(
                    state.incidents,
                    state.user.role,
                    { screen = Screen.INCIDENTS },
                    {
                        val permissions = buildList {
                            add(Manifest.permission.ACCESS_FINE_LOCATION)
                            add(Manifest.permission.CAMERA)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray()
                        permissionLauncher.launch(permissions)
                        screen = Screen.NEW
                    }
                )
                Screen.INCIDENTS -> IncidentListScreen(
                    state.incidents.filter { it.public || it.author == state.user.name },
                    { vm.selectIncident(it); screen = Screen.DETAIL }
                )
                Screen.NEW -> NewIncidentScreen({ screen = Screen.INCIDENTS }) { title, desc, cat, sit, pub, loc, mediaUri ->
                    vm.addIncident(title, desc, cat, sit, pub, loc, mediaUri) { submitted ->
                        if (submitted) screen = Screen.INCIDENTS
                    }
                }
                Screen.PROFILE -> ProfileScreen(state.user, { screen = Screen.LOGIN })
                Screen.DETAIL -> state.selectedIncident?.let {
                    IncidentDetailScreen(it, state.user.role == UserRole.OPERATOR, state.messages, vm::advanceStatus, vm::sendMessage)
                }
                Screen.OPERATOR -> OperatorScreen(state.incidents.filter { it.status != IncidentStatus.CLOSED }, vm::advanceStatus)
                Screen.LOGIN -> Unit
            }
        }
    }
}

@Composable
private fun DashboardScreen(incidents: List<Incident>, role: UserRole, onIncidents: () -> Unit, onNew: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (role == UserRole.ANONYMOUS) "Make your city better" else "Good to see you",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        if (role == UserRole.ANONYMOUS) "See what needs attention nearby and follow local progress."
                        else "Report an issue or follow the ones that matter to you.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Total", incidents.size.toString(), MaterialTheme.colorScheme.primaryContainer, Modifier.weight(1f))
                StatCard("Active", incidents.count { it.status != IncidentStatus.CLOSED }.toString(), MaterialTheme.colorScheme.secondaryContainer, Modifier.weight(1f))
                StatCard("Avg. repair", "2.4 d", Color(0xFFF4E4C8), Modifier.weight(1f))
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Open reports", style = MaterialTheme.typography.titleMedium)
                    Text("Browse active issues and see how your municipality is responding.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onIncidents) { Text("Explore incidents") }
                }
            }
        }
        if (role != UserRole.ANONYMOUS) item {
            OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Report an incident")
            }
        }
    }
}

@Composable private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IncidentListScreen(incidents: List<Incident>, onSelect: (Incident) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Incidents", style = MaterialTheme.typography.headlineSmall)
            Text("Active reports from the community", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(incidents, key = { it.id }, contentType = { "incident" }) { incident ->
            IncidentCard(incident, onSelect)
        }
    }
}

@Composable private fun IncidentCard(incident: Incident, onSelect: (Incident) -> Unit) {
    Card(
        onClick = { onSelect(incident) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(incident.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusChip(incident.status)
            }
            Text("${incident.category}  •  ${incident.situation.name.lowercase().replace('_', ' ')}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(incident.description, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
            Text("${incident.supporters} supporters", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun StatusChip(status: IncidentStatus) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            status.name.replace('_', ' ').lowercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable private fun IncidentDetailScreen(incident: Incident, operator: Boolean, messages: List<pt.isec.amov.cityfix.model.IncidentMessage>, advance: (Incident) -> Unit, sendMessage: (String) -> Unit) {
    var messageText by remember { mutableStateOf("") }
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(incident.title, style = MaterialTheme.typography.headlineSmall)
        Text(incident.description)
        HorizontalDivider()
        Text("Category: ${incident.category}")
        Text("Situation: ${incident.situation.name.lowercase().replace('_', ' ')}")
        Text("Status: ${incident.status.name.replace('_', ' ')}")
        Text("Location: ${incident.location}")
        Text("Media attached: ${if (incident.hasMedia) "yes" else "no"}")
        if (incident.mediaUrl.isNotBlank()) {
            OutlinedButton({
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(incident.mediaUrl)))
            }) { Text("Open attached media") }
        }
        if (operator && incident.status != IncidentStatus.CLOSED) Button({ advance(incident) }) { Text("Advance ticket status") }
        Text("Messages with municipal services", style = MaterialTheme.typography.titleMedium)
        messages.forEach { message ->
            Text("${message.authorName}: ${message.text}", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(messageText, { messageText = it }, modifier = Modifier.weight(1f), placeholder = { Text("Type a message...") })
            Button({
                sendMessage(messageText)
                messageText = ""
            }, modifier = Modifier.padding(start = 8.dp)) { Text("Send") }
        }
    }
}

@Composable
private fun NewIncidentScreen(onCancel: () -> Unit, onSubmit: (String, String, String, IncidentSituation, Boolean, String, Uri?) -> Unit) {
    var title by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Public roads") }; var location by remember { mutableStateOf("") }
    var situation by remember { mutableStateOf(IncidentSituation.DAMAGE) }; var public by remember { mutableStateOf(true) }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var locationMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { mediaUri = it }
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    fun useCurrentLocation() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) {
            locationMessage = "Location permission is required."
            return
        }
        locationMessage = "Getting current location..."
        locationClient.lastLocation
            .addOnSuccessListener { currentLocation ->
                if (currentLocation == null) {
                    locationMessage = "Location unavailable. Enter it manually."
                } else {
                    location = "${currentLocation.latitude}, ${currentLocation.longitude}"
                    locationMessage = "Current location selected."
                }
            }
            .addOnFailureListener {
                locationMessage = "Could not read location. Enter it manually."
            }
    }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Report incident", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3)
        OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Location") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(::useCurrentLocation) { Text("Use current location") }
            if (locationMessage.isNotBlank()) Text(locationMessage, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { IncidentSituation.entries.forEach { FilterChip(situation == it, { situation = it }, label = { Text(it.name.lowercase()) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { FilterChip(public, { public = !public }, label = { Text(if (public) "Public" else "Private") }); Spacer(Modifier.padding(4.dp)); OutlinedButton({ mediaPicker.launch("image/* video/*") }) { Text(if (mediaUri != null) "Media selected" else "Attach photo/video") } }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button({ if (title.isNotBlank() && description.isNotBlank() && location.isNotBlank() && mediaUri != null) onSubmit(title, description, category, situation, public, location, mediaUri) }, Modifier.weight(1f)) { Text("Submit") }
        }
    }
}

@Composable private fun ProfileScreen(user: pt.isec.amov.cityfix.model.CityFixUser, login: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Profile", style = MaterialTheme.typography.headlineSmall); Text("Name: ${user.name}"); Text("Email: ${user.email.ifBlank { "Not authenticated" }}"); Text("Role: ${user.role.name.lowercase()}"); if (user.role == UserRole.ANONYMOUS) Button(login) { Text("Sign in or register") } }
}

@Composable private fun OperatorScreen(incidents: List<Incident>, advance: (Incident) -> Unit) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Operational management", style = MaterialTheme.typography.headlineSmall) }; items(incidents) { IncidentCard(it) { advance(it) } } }
}

@Composable private fun LoginScreen(onBack: () -> Unit, onLogin: (String, String, String) -> Unit, onGuest: () -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("CityFix authentication", style = MaterialTheme.typography.headlineSmall); OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }); OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation()); Button({ if (name.isNotBlank() && email.contains("@") && password.length >= 6) onLogin(name, email, password) }, Modifier.fillMaxWidth()) { Text("Sign in / Register") }; OutlinedButton(onGuest, Modifier.fillMaxWidth()) { Text("Continue as anonymous citizen") }; Text("A conta é autenticada pelo Firebase. O acesso de operador é atribuído pela administração.", style = MaterialTheme.typography.bodySmall); OutlinedButton(onBack) { Text("Back") } }
}
