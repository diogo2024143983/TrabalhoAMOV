package pt.isec.amov.cityfix.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val thumb = incident.thumbnailUrl.ifBlank { if (incident.isVideo) "" else incident.mediaUrl }
            Box(
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = "Miniatura de ${incident.title}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (incident.isVideo) {
                        Icon(Icons.Default.PlayCircle, contentDescription = "Vídeo", tint = Color.White)
                    }
                } else if (incident.mediaPending) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "Multimédia a enviar")
                }
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
    loading: Boolean,
    onSupport: () -> Unit,
    onChangeStatus: (IncidentStatus) -> Unit,
    onRegisterWorkStart: () -> Unit,
    onRequestInfo: (String) -> Unit,
    onResolveWithProof: (Uri) -> Unit,
    onReopen: (String) -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageText by rememberSaveable { mutableStateOf("") }
    var reopenReason by rememberSaveable { mutableStateOf("") }
    var showReopenDialog by rememberSaveable { mutableStateOf(false) }
    var infoText by rememberSaveable { mutableStateOf("") }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var proofUri by remember { mutableStateOf<Uri?>(null) }

    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) proofUri = uri
    }

    val isAnonymous = currentUser.role == UserRole.ANONYMOUS
    val isOperator = currentUser.role == UserRole.OPERATOR
    val isAuthor = currentUser.id == incident.authorId
    val isClosed = incident.status == IncidentStatus.CLOSED
    val canReopen = CityFixUtils.canReopenIncident(incident) && (isAuthor || isOperator)
    val hasSupported = incident.supporterIds.contains(currentUser.id)
    val canAskClarification = isAuthor && !isOperator &&
        incident.status in listOf(IncidentStatus.OPEN, IncidentStatus.IN_RESOLUTION)

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
                    enabled = reopenReason.isNotBlank(),
                    onClick = {
                        onReopen(reopenReason)
                        reopenReason = ""
                        showReopenDialog = false
                    }
                ) { Text("Confirmar Reabertura") }
            },
            dismissButton = {
                TextButton(onClick = { showReopenDialog = false }) { Text("Cancelar") }
            }
        )
    }

    if (showInfoDialog) {
        AlertDialog(
            onDismissRequest = { showInfoDialog = false },
            title = { Text(if (isOperator) "Pedir informações ao munícipe" else "Pedir esclarecimentos à autarquia") },
            text = {
                OutlinedTextField(
                    value = infoText,
                    onValueChange = { infoText = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    label = { Text(if (isOperator) "Que informação é necessária? *" else "Ex.: estimativa temporal, procedimento *") }
                )
            },
            confirmButton = {
                Button(
                    enabled = infoText.isNotBlank(),
                    onClick = {
                        onRequestInfo(infoText)
                        infoText = ""
                        showInfoDialog = false
                    }
                ) { Text("Enviar pedido") }
            },
            dismissButton = {
                TextButton(onClick = { showInfoDialog = false }) { Text("Cancelar") }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(incident.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                StatusChip(incident.status)
            }
            Text(incident.description)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            MediaPreview(
                url = incident.mediaUrl,
                thumbnailUrl = incident.thumbnailUrl,
                isVideo = incident.isVideo,
                pending = incident.mediaPending,
                description = "Multimédia da ocorrência"
            )
        }

        if (incident.status == IncidentStatus.INFORMATION_REQUEST) {
            item {
                val byOperator = incident.infoRequestedByRole == UserRole.OPERATOR.name
                val waitingForMe = (byOperator && !isOperator) || (!byOperator && isOperator)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        text = (if (byOperator) "A autarquia pediu informações complementares." else "O munícipe pediu esclarecimentos.") +
                            if (waitingForMe) " Responda nas mensagens abaixo." else " A aguardar resposta.",
                        modifier = Modifier.padding(12.dp)
                    )
                }
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
            if (incident.status == IncidentStatus.RESOLVED && incident.resolvedAt != null) {
                Text(
                    "Encerramento automático em ${remainingUntilClose(incident.resolvedAt)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (incident.latitude != 0.0 && incident.longitude != 0.0) {
            item {
                val context = LocalContext.current
                OutlinedButton(onClick = {
                    val zoom = if (isAnonymous) 12 else 17
                    val uri = Uri.parse("geo:${incident.latitude},${incident.longitude}?z=$zoom")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }) {
                    Text(if (isAnonymous) "Ver Mapa (Zoom Afastado)" else "Ver Localização no Mapa")
                }
            }
        }

        if (incident.status in listOf(IncidentStatus.RESOLVED, IncidentStatus.CLOSED) &&
            (incident.resolutionProofUrl.isNotBlank() || incident.proofPending)
        ) {
            item {
                Text("Prova de Resolução", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                MediaPreview(
                    url = incident.resolutionProofUrl,
                    thumbnailUrl = incident.resolutionProofThumbUrl,
                    isVideo = incident.resolutionProofIsVideo,
                    pending = incident.proofPending,
                    description = "Prova de resolução"
                )
            }
        }

        // Botão Apoiar Ocorrência
        if (!isAnonymous && !hasSupported && !isClosed) {
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
                            OutlinedButton(onClick = { showInfoDialog = true }) { Text("Pedir Info") }
                            Button(onClick = { onChangeStatus(IncidentStatus.IN_RESOLUTION) }) { Text("Iniciar Resolução") }
                        }
                    }
                    IncidentStatus.INFORMATION_REQUEST -> {
                        Button(onClick = { onChangeStatus(IncidentStatus.IN_RESOLUTION) }) { Text("Iniciar Resolução") }
                    }
                    IncidentStatus.IN_RESOLUTION -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (incident.workStartedAt != null) {
                                Text("Trabalhos iniciados a ${formatDate(incident.workStartedAt)}")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (incident.workStartedAt == null) {
                                    Button(onClick = onRegisterWorkStart) { Text("Registar início dos trabalhos") }
                                }
                                OutlinedButton(onClick = { showInfoDialog = true }) { Text("Pedir Info") }
                            }
                            OutlinedButton(onClick = {
                                proofPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                            }) {
                                Text(if (proofUri != null) "Prova selecionada ✓ (alterar)" else "Anexar foto/vídeo do trabalho concluído *")
                            }
                            Button(
                                onClick = { proofUri?.let(onResolveWithProof); proofUri = null },
                                enabled = proofUri != null && !loading
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.size(8.dp))
                                }
                                Text("Marcar como Resolvido")
                            }
                        }
                    }
                    IncidentStatus.RESOLVED -> {
                        Text("Aguarda o prazo de reabertura. O ticket é encerrado automaticamente após 5 dias.")
                    }
                    IncidentStatus.CLOSED -> {
                        Text("Esta ocorrência está definitivamente encerrada.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (canAskClarification) {
            item {
                OutlinedButton(onClick = { showInfoDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Pedir esclarecimentos à autarquia")
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
                    Text("Reabrir Ocorrência (até 5 dias após a resolução)")
                }
            }
        }

        // Histórico completo do ticket (transições com timestamp)
        if (!isAnonymous && incident.history.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Histórico", style = MaterialTheme.typography.titleMedium)
            }
            items(incident.history.reversed()) { entry ->
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "${formatDate(entry.at)} • ${incidentStatusLabel(entry.status)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        listOf(entry.byName, entry.note).filter { it.isNotBlank() }.joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Troca de Mensagens (Apenas para utilizadores autenticados)
        if (!isAnonymous) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Comunicação com os Serviços Camarários", style = MaterialTheme.typography.titleMedium)
            }

            items(messages, key = { it.id }) { msg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            "${msg.authorName} (${roleLabel(msg.authorRole)}) • ${formatDate(msg.createdAt)}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                if (isClosed) {
                    Text("Ticket encerrado: não admite novas mensagens.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Escreva uma mensagem...") }
                        )
                        Spacer(Modifier.size(8.dp))
                        Button(
                            enabled = messageText.isNotBlank(),
                            onClick = {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        ) {
                            Text("Enviar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreview(url: String, thumbnailUrl: String, isVideo: Boolean, pending: Boolean, description: String) {
    val context = LocalContext.current
    when {
        url.isBlank() && pending -> Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudUpload, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Multimédia em envio (será carregada quando houver rede)…")
            }
        }
        url.isBlank() -> Unit
        isVideo -> Box(
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .clickable {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = description,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Reproduzir vídeo",
                tint = Color.White,
                modifier = Modifier.size(56.dp)
            )
        }
        else -> AsyncImage(
            model = url,
            contentDescription = description,
            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop
        )
    }
}

private fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))

private fun remainingUntilClose(resolvedAt: Long): String {
    val remaining = (resolvedAt + CityFixUtils.REOPEN_WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0)
    val hours = remaining / (60 * 60 * 1000)
    return if (hours >= 24) "${hours / 24} dia(s) e ${hours % 24} h" else "$hours h"
}

private fun roleLabel(role: UserRole): String = when (role) {
    UserRole.OPERATOR -> "Operador"
    UserRole.CITIZEN -> "Munícipe"
    UserRole.ANONYMOUS -> "Anónimo"
}

// =========================================================================
// 3. NOVA OCORRÊNCIA
// =========================================================================

@Composable
fun NewIncidentScreen(
    categories: List<IncidentCategory>,
    loading: Boolean,
    onCancel: () -> Unit,
    onSubmit: (String, String, String, String, IncidentSituation, Boolean, Double, Double, String, Uri?) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(categories.firstOrNull() ?: IncidentCategory(id = "vias", name = "Vias Públicas")) }
    var situation by rememberSaveable { mutableStateOf(IncidentSituation.DAMAGE) }
    var isPublic by rememberSaveable { mutableStateOf(true) }
    var latitude by rememberSaveable { mutableDoubleStateOf(CityFixUtils.PRACA_REPUBLICA_LAT) }
    var longitude by rememberSaveable { mutableDoubleStateOf(CityFixUtils.PRACA_REPUBLICA_LON) }
    var locationAddress by rememberSaveable { mutableStateOf("Coimbra") }
    var mediaUri by remember { mutableStateOf<Uri?>(null) }
    var locationMessage by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) mediaUri = uri
    }
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
                    locationAddress = String.format(Locale.US, "%.4f, %.4f", loc.latitude, loc.longitude)
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
        if (categories.isNotEmpty() && categories.none { it.id == selectedCategory.id }) {
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
                OutlinedButton(onClick = {
                    mediaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                }) {
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
                    enabled = !loading,
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
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Submeter")
                    }
                }
            }
        }
    }
}

fun incidentSituationLabel(situation: IncidentSituation): String = when (situation) {
    IncidentSituation.DAMAGE -> "Dano"
    IncidentSituation.BREAKDOWN -> "Avaria"
    IncidentSituation.DESTRUCTION -> "Destruição"
    IncidentSituation.THEFT -> "Roubo"
}

fun incidentStatusLabel(status: IncidentStatus): String = when (status) {
    IncidentStatus.OPEN -> "Aberta"
    IncidentStatus.INFORMATION_REQUEST -> "Pedido Info"
    IncidentStatus.IN_RESOLUTION -> "Em Resolução"
    IncidentStatus.RESOLVED -> "Resolvida"
    IncidentStatus.CLOSED -> "Encerrada"
}
