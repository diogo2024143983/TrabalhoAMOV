package pt.isec.amov.cityfix

import android.Manifest
import android.os.Build
import android.os.Bundle
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
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.viewmodel.CityFixViewModel

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
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val permissions = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }
    LaunchedEffect(state.message) {
        state.message?.let { scope.launch { snackbar.showSnackbar(it) } }
    }
    if (screen == Screen.LOGIN) {
        LoginScreen(onBack = { screen = Screen.DASHBOARD }, onLogin = { n, e, op ->
            vm.signIn(n, e, op); screen = if (op) Screen.OPERATOR else Screen.DASHBOARD
        }, onGuest = { vm.continueAsGuest(); screen = Screen.DASHBOARD })
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (screen == Screen.DETAIL) "Incident details" else "CityFix") },
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
            NavigationBar {
                NavigationBarItem(screen == Screen.DASHBOARD, { screen = Screen.DASHBOARD }, { Icon(Icons.Default.Home, "Dashboard") }, label = { Text("Dashboard") })
                NavigationBarItem(screen == Screen.INCIDENTS, { screen = Screen.INCIDENTS }, { Icon(Icons.Default.List, "Incidents") }, label = { Text("Incidents") })
                NavigationBarItem(screen == Screen.PROFILE, { screen = Screen.PROFILE }, { Icon(Icons.Default.Person, "Profile") }, label = { Text("Profile") })
                if (state.user.role == UserRole.OPERATOR) NavigationBarItem(screen == Screen.OPERATOR, { screen = Screen.OPERATOR }, { Icon(Icons.Default.Check, "Management") }, label = { Text("Manage") })
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Surface(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.DASHBOARD -> DashboardScreen(state.incidents, state.user.role, { screen = Screen.INCIDENTS }, { screen = Screen.NEW })
                Screen.INCIDENTS -> IncidentListScreen(state.incidents.filter { it.public || it.author == state.user.name }, { vm.selectIncident(it); screen = Screen.DETAIL } )
                Screen.NEW -> NewIncidentScreen({ screen = Screen.INCIDENTS }) { title, desc, cat, sit, pub, loc ->
                    vm.addIncident(title, desc, cat, sit, pub, loc); screen = Screen.INCIDENTS
                }
                Screen.PROFILE -> ProfileScreen(state.user, { screen = Screen.LOGIN })
                Screen.DETAIL -> state.selectedIncident?.let { IncidentDetailScreen(it, state.user.role == UserRole.OPERATOR, vm::advanceStatus) }
                Screen.OPERATOR -> OperatorScreen(state.incidents.filter { it.status != IncidentStatus.CLOSED }, vm::advanceStatus)
                Screen.LOGIN -> Unit
            }
        }
    }
}

@Composable
private fun DashboardScreen(incidents: List<Incident>, role: UserRole, onIncidents: () -> Unit, onNew: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Civic participation", style = MaterialTheme.typography.headlineSmall) }
        item { Text(if (role == UserRole.ANONYMOUS) "Public incidents near you" else "Welcome back to your municipal dashboard") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("Total", incidents.size.toString(), Modifier.weight(1f))
                StatCard("Active", incidents.count { it.status != IncidentStatus.CLOSED }.toString(), Modifier.weight(1f))
                StatCard("Avg. repair", "2.4 d", Modifier.weight(1f))
            }
        }
        item { Button(onClick = onIncidents, modifier = Modifier.fillMaxWidth()) { Text("View public incidents") } }
        if (role != UserRole.ANONYMOUS) item { OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.padding(4.dp)); Text("Report incident") } }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) {
    Card(modifier) { Column(Modifier.padding(10.dp)) { Text(value, style = MaterialTheme.typography.titleLarge); Text(label, style = MaterialTheme.typography.labelSmall) } }
}

@Composable
private fun IncidentListScreen(incidents: List<Incident>, onSelect: (Incident) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Public incidents", style = MaterialTheme.typography.headlineSmall) }
        items(incidents, key = { it.id }) { incident -> IncidentCard(incident, onSelect) }
    }
}

@Composable private fun IncidentCard(incident: Incident, onSelect: (Incident) -> Unit) {
    Card(onClick = { onSelect(incident) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(incident.title, style = MaterialTheme.typography.titleMedium)
            Text("${incident.category} · ${incident.situation.name.lowercase().replace('_', ' ')}")
            Text(incident.description, maxLines = 2)
            Text("${incident.status.name.replace('_', ' ')} · ${incident.supporters} supporters", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable private fun IncidentDetailScreen(incident: Incident, operator: Boolean, advance: (Incident) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(incident.title, style = MaterialTheme.typography.headlineSmall)
        Text(incident.description)
        Divider()
        Text("Category: ${incident.category}")
        Text("Situation: ${incident.situation.name.lowercase().replace('_', ' ')}")
        Text("Status: ${incident.status.name.replace('_', ' ')}")
        Text("Location: ${incident.location}")
        Text("Media attached: ${if (incident.hasMedia) "yes" else "no"}")
        if (operator && incident.status != IncidentStatus.CLOSED) Button({ advance(incident) }) { Text("Advance ticket status") }
        Text("Messages with municipal services", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField("", {}, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Type a message...") })
    }
}

@Composable
private fun NewIncidentScreen(onCancel: () -> Unit, onSubmit: (String, String, String, IncidentSituation, Boolean, String) -> Unit) {
    var title by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Public roads") }; var location by remember { mutableStateOf("") }
    var situation by remember { mutableStateOf(IncidentSituation.DAMAGE) }; var public by remember { mutableStateOf(true) }
    var hasMedia by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Report incident", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, minLines = 3)
        OutlinedTextField(category, { category = it }, Modifier.fillMaxWidth(), label = { Text("Category") }, singleLine = true)
        OutlinedTextField(location, { location = it }, Modifier.fillMaxWidth(), label = { Text("Location") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { IncidentSituation.entries.forEach { FilterChip(situation == it, { situation = it }, label = { Text(it.name.lowercase()) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { FilterChip(public, { public = !public }, label = { Text(if (public) "Public" else "Private") }); Spacer(Modifier.padding(4.dp)); OutlinedButton({ hasMedia = true }) { Text(if (hasMedia) "Media selected" else "Attach photo/video") } }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onCancel, Modifier.weight(1f)) { Text("Cancel") }
            Button({ if (title.isNotBlank() && description.isNotBlank() && location.isNotBlank() && hasMedia) onSubmit(title, description, category, situation, public, location) }, Modifier.weight(1f)) { Text("Submit") }
        }
    }
}

@Composable private fun ProfileScreen(user: pt.isec.amov.cityfix.model.CityFixUser, login: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { Text("Profile", style = MaterialTheme.typography.headlineSmall); Text("Name: ${user.name}"); Text("Email: ${user.email.ifBlank { "Not authenticated" }}"); Text("Role: ${user.role.name.lowercase()}"); if (user.role == UserRole.ANONYMOUS) Button(login) { Text("Sign in or register") } }
}

@Composable private fun OperatorScreen(incidents: List<Incident>, advance: (Incident) -> Unit) {
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { item { Text("Operational management", style = MaterialTheme.typography.headlineSmall) }; items(incidents) { IncidentCard(it) { advance(it) } } }
}

@Composable private fun LoginScreen(onBack: () -> Unit, onLogin: (String, String, Boolean) -> Unit, onGuest: () -> Unit) {
    var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var operator by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("CityFix authentication", style = MaterialTheme.typography.headlineSmall); OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }); OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text("Email") }); OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, visualTransformation = PasswordVisualTransformation()); FilterChip(operator, { operator = !operator }, label = { Text("Municipal operator") }); Button({ if (name.isNotBlank() && email.contains("@") && password.length >= 6) onLogin(name, email, operator) }, Modifier.fillMaxWidth()) { Text("Sign in / Register") }; OutlinedButton(onGuest, Modifier.fillMaxWidth()) { Text("Continue as anonymous citizen") }; Text("MFA and password recovery are provided by Firebase when configured.", style = MaterialTheme.typography.bodySmall); OutlinedButton(onBack) { Text("Back") } }
}
