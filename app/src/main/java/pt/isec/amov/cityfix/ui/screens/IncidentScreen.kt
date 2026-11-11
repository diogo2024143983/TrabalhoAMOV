package pt.isec.amov.cityfix.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils

// =========================================================================
// 1. LISTA DE OCORRÊNCIAS
// =========================================================================

@Composable
fun IncidentListScreen(
    incidents: List<Incident>,
    onSelect: (Incident) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Ocorrências Registadas", style = MaterialTheme.typography.headlineSmall)
            Text("Lista de problemas reportados pela comunidade", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(incidents, key = { it.id }) { incident ->
            IncidentCard(incident, onSelect)
        }
    }
}

@Composable
fun IncidentCard(incident: Incident, onSelect: (Incident) -> Unit) {
    Card(
        onClick = { onSelect(incident) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (incident.mediaUrl.isNotBlank()) {
                AsyncImage(
                    model = incident.thumbnailUrl.ifBlank { incident.mediaUrl },
                    contentDescription = "Thumbnail",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = incident.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChip(incident.status)
                }
                Text(
                    text = "${incident.categoryName.ifBlank { "Geral" }}  •  ${incidentSituationLabel(incident.situation)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = incident.description,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "${incident.supporterIds.size} apoiantes",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: IncidentStatus) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = incidentStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// =========================================================================
// 2. DETALHE DA OCORRÊNCIA
// =========================================================================

@Composable
fun IncidentDetailScreen(
    incident: Incident,
    currentUser: CityFixUser,
    messages: List<IncidentMessage>,
    onSupport: () -> Unit,
    onUpdateStatus: (IncidentStatus, String) -> Unit,
    onReopen: (String) -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var reopenReason by remember { mutableStateOf("") }
    var showReopenDialog by remember { mutableStateOf(false) }
    var proofUrl by remember { mutableStateOf("") }
    val context = LocalContext.current

    val isAnonymous = currentUser.role == UserRole.ANONYMOUS
    val isOperator = currentUser.role == UserRole.OPERATOR
    val isAuthor = currentUser.id == incident.authorId
    val canReopen = CityFixUtils.canReopenIncident(incident) && (isAuthor || isOperator)
    val hasSupported = incident.supporterIds.contains(currentUser.id)

    if (showReopenDialog) {
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = { Text("Reabrir Ocorrência (Prazo de 5 dias)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Indique o motivo pelo qual o problema persiste:")
                    OutlinedTextField(
                        value = reopenReason,
                        onValueChange = { reopenReason = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Motivo da Reabertura *") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reopenReason.isNotBlank()) {
                            onReopen(reopenReason)
                            showReopenDialog = false
                        }
                    }
                ) { Text("Confirmar Reabertura") }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(incident.title, style = MaterialTheme.typography.headlineSmall)
            Text(incident.description)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        // Imagem da Ocorrência
        if (incident.mediaUrl.isNotBlank()) {
            item {
                AsyncImage(
                    model = incident.mediaUrl,
                    contentDescription = "Fotografia da ocorrência",
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        item {
            Text("Categoria: ${incident.categoryName.ifBlank { "Geral" }}")
            Text("Situação: ${incidentSituationLabel(incident.situation)}")
            Text("Estado: ${incidentStatusLabel(incident.status)}")

            // Alínea 1.C: Anónimo oculta dados pessoais do autor e georreferenciação exata
            if (!isAnonymous) {
                Text("Autor: ${incident.authorName}")
                if (isOperator && incident.authorPhone.isNotBlank()) {
                    Text("Contacto Autor: ${incident.authorPhone}")
                }
                Text("Localização: ${incident.locationAddress.ifBlank { "${incident.latitude}, ${incident.longitude}" }}")
            } else {
                Text("Autor e morada ocultos (Perfil Anónimo)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (incident.reopenReason.isNotBlank()) {
                Text("Motivo de Reabertura: ${incident.reopenReason}", color = MaterialTheme.colorScheme.error)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (incident.latitude != 0.0 && incident.longitude != 0.0) {
                    OutlinedButton(onClick = {
                        val zoom = if (isAnonymous) 12 else 17
                        val uri = Uri.parse("geo:${incident.latitude},${incident.longitude}?z=$zoom")
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }) {
                        Text(if (isAnonymous) "Ver Mapa (Zoom Afastado)" else "Ver Localização no Mapa")
                    }
                }
                if (incident.resolutionProofUrl.isNotBlank()) {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(incident.resolutionProofUrl)))
                    }) {
                        Text("Ver Prova de Resolução")
                    }
                }
            }
        }

        // Botão Apoiar Ocorrência
        if (!isAnonymous && !hasSupported) {
            item {
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Apoiar Ocorrência (${incident.supporterIds.size} apoios)")
                }
            }
        }

        // Controlo do Ciclo de Vida pelo Operador
        if (isOperator) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Gestão Operacional", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                when (incident.status) {
                    IncidentStatus.OPEN -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onUpdateStatus(IncidentStatus.INFORMATION_REQUEST, "") }) {
                                Text("Pedir Info")
                            }
                            Button(onClick = { onUpdateStatus(IncidentStatus.IN_RESOLUTION, "") }) {
                                Text("Iniciar Resolução")
                            }
                        }
                    }
                    IncidentStatus.INFORMATION_REQUEST -> {
                        Button(onClick = { onUpdateStatus(IncidentStatus.IN_RESOLUTION, "") }) {
                            Text("Iniciar Resolução")
                        }
                    }
                    IncidentStatus.IN_RESOLUTION -> {
                        OutlinedTextField(
                            value = proofUrl,
                            onValueChange = { proofUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("URL / Prova de Resolução (Obrigatório) *") }
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { onUpdateStatus(IncidentStatus.RESOLVED, proofUrl) },
                            enabled = proofUrl.isNotBlank()
                        ) {
                            Text("Marcar como Resolvido com Prova")
                        }
                    }
                    IncidentStatus.RESOLVED -> {
                        Button(onClick = { onUpdateStatus(IncidentStatus.CLOSED, "") }) {
                            Text("Encerrar Ocorrência")
                        }
                    }
                    IncidentStatus.CLOSED -> {
                        Text("Esta ocorrência está definitivamente encerrada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Reabertura dentro do prazo de 5 dias
        if (incident.status == IncidentStatus.RESOLVED && canReopen) {
            item {
                OutlinedButton(
                    onClick = { showReopenDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reabrir Ocorrência (Restam menos de 5 dias)")
                }
            }
        }

        // Troca de Mensagens (Apenas para utilizadores autenticados)
        if (!isAnonymous) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Comunicação com os Serviços Camarários", style = MaterialTheme.typography.titleMedium)
            }

            items(messages) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("${msg.authorName} (${msg.authorRole}):", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Escreva uma mensagem...") }
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    }) {
                        Text("Enviar")
                    }
                }
            }
        }
    }
}

// =========================================================================
// 3. NOVA OCORRÊNCIA
// =========================================================================

@Composable
fun NewIncidentScreen(
    categories: List<IncidentCategory>,
    onCancel: () -> Unit,
    onSubmit: (String, String, String, String, IncidentSituation, Boolean, Double, Double, String, Uri?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: IncidentCategory(id = "vias", name = "Vias Públicas")) }
    var situation by remember { mutableStateOf(IncidentSituation.DAMAGE) }
    var isPublic by remember { mutableStateOf(true) }
    var latitude by remember { mutableDoubleStateOf(CityFixUtils.PRACA_REPUBLICA_LAT) }
    var longitude by remember { mutableDoubleStateOf(CityFixUtils.PRACA_REPUBLICA_LON) }
    var locationAddress by remember { mutableStateOf("Coimbra") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var locationMessage by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { mediaUri = it }
    val locationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchCurrentLocation() {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) {
            locationMessage = "Permissão de GPS necessária."
            return
        }
        locationMessage = "A obter localização atual..."
        locationClient.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    latitude = loc.latitude
                    longitude = loc.longitude
                    locationAddress = String.format("%.4f, %.4f", loc.latitude, loc.longitude)
                    locationMessage = "Localização obtida com sucesso!"
                } else {
                    locationMessage = "GPS indisponível. Usando valor padrão."
                }
            }
            .addOnFailureListener {
                locationMessage = "Erro ao ler GPS."
            }
    }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedCategory.id.isBlank()) {
            selectedCategory = categories.first()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Registar Ocorrência", style = MaterialTheme.typography.headlineSmall)
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título da ocorrência *") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrição detalhada *") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        }
        item {
            val availableCategories = categories.ifEmpty {
                listOf(
                    IncidentCategory(id = "vias", name = "Vias Públicas"),
                    IncidentCategory(id = "agua", name = "Água"),
                    IncidentCategory(id = "iluminacao", name = "Iluminação"),
                    IncidentCategory(id = "residuos", name = "Resíduos")
                )
            }
            Text("Categoria:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                availableCategories.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory.name == cat.name,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.name) }
                    )
                }
            }
        }
        item {
            Text("Situação do Problema:", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IncidentSituation.entries.forEach { sit ->
                    FilterChip(
                        selected = situation == sit,
                        onClick = { situation = sit },
                        label = { Text(incidentSituationLabel(sit)) }
                    )
                }
            }
        }
        item {
            OutlinedTextField(
                value = locationAddress,
                onValueChange = { locationAddress = it },
                label = { Text("Morada / Localização *") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = ::fetchCurrentLocation) {
                    Text("Usar GPS Atual")
                }
                if (locationMessage.isNotBlank()) {
                    Text(locationMessage, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = isPublic,
                    onClick = { isPublic = !isPublic },
                    label = { Text(if (isPublic) "Ocorrência Pública" else "Ocorrência Privada") }
                )
                Spacer(Modifier.size(8.dp))
                OutlinedButton(onClick = { mediaPicker.launch("image/* video/*") }) {
                    Text(if (mediaUri != null) "Foto/Vídeo Selecionado ✓" else "Anexar Foto/Vídeo *")
                }
            }
        }

        if (!formError.isNullOrBlank()) {
            item {
                Text(formError!!, color = MaterialTheme.colorScheme.error)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text("Cancelar")
                }
                Button(
                    onClick = {
                        if (title.isBlank() || description.isBlank() || mediaUri == null) {
                            formError = "Preenche o título, descrição e anexa uma fotografia/vídeo (obrigatório)."
                        } else {
                            formError = null
                            onSubmit(
                                title,
                                description,
                                selectedCategory.id.ifBlank { "geral" },
                                selectedCategory.name.ifBlank { "Geral" },
                                situation,
                                isPublic,
                                latitude,
                                longitude,
                                locationAddress,
                                mediaUri
                            )
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Submeter")
                }
            }
        }
    }
}

fun incidentSituationLabel(situation: IncidentSituation): String = when (situation) {
    IncidentSituation.DAMAGE -> "Dano"
    IncidentSituation.BREAKDOWN -> "Avaria"
    IncidentSituation.DESTRUCTION -> "Destruição"
    IncidentSituation.THEFT -> "Furto"
}

fun incidentStatusLabel(status: IncidentStatus): String = when (status) {
    IncidentStatus.OPEN -> "Aberta"
    IncidentStatus.INFORMATION_REQUEST -> "Pedido Info"
    IncidentStatus.IN_RESOLUTION -> "Em Resolução"
    IncidentStatus.RESOLVED -> "Resolvida"
    IncidentStatus.CLOSED -> "Encerrada"
}