package pt.isec.amov.cityfix.ui.screens

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import pt.isec.amov.cityfix.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentList
import pt.isec.amov.cityfix.model.IncidentMessage
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.MediaAttachment
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.components.IncidentMap
import pt.isec.amov.cityfix.ui.components.IncidentPhotoGallery
import pt.isec.amov.cityfix.ui.components.StatusChip
import pt.isec.amov.cityfix.ui.components.formatDate
import pt.isec.amov.cityfix.ui.components.incidentSituationLabel
import pt.isec.amov.cityfix.ui.components.incidentStatusLabel
import pt.isec.amov.cityfix.ui.components.roleLabel

/**
 * Detalhe de uma ocorrência (sem estado de negócio: recebe dados e emite eventos).
 * Em landscape divide o ecrã 50/50 entre os detalhes e o mapa (layout adaptativo de P10).
 */
@Composable
fun IncidentDetailScreen(
    incident: Incident?,
    currentUser: CityFixUser,
    messages: List<IncidentMessage>,
    loading: Boolean,
    onSupport: () -> Unit,
    onChangeStatus: (IncidentStatus) -> Unit,
    onRegisterWorkStart: () -> Unit,
    onRequestInfo: (String) -> Unit,
    onResolveWithProof: (Uri) -> Unit,
    onReopen: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (incident == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.detail_not_found))
        }
        return
    }

    var messageText by rememberSaveable { mutableStateOf("") }
    var reopenReason by rememberSaveable { mutableStateOf("") }
    var showReopenDialog by rememberSaveable { mutableStateOf(false) }
    var infoText by rememberSaveable { mutableStateOf("") }
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    var proofUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val proofPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) proofUri = uri
    }

    val isAnonymous = currentUser.role == UserRole.ANONYMOUS
    val isOperator = currentUser.role == UserRole.OPERATOR
    val isAuthor = currentUser.id == incident.authorId
    val isClosed = incident.status == IncidentStatus.CLOSED
    val canReopen = IncidentList.canReopen(incident) && (isAuthor || isOperator)
    val hasSupported = incident.supporterIds.contains(currentUser.id)
    val canAskClarification = isAuthor && !isOperator &&
        incident.status in listOf(IncidentStatus.OPEN, IncidentStatus.IN_RESOLUTION)

    if (showReopenDialog) {
        TextInputDialog(
            title = stringResource(R.string.detail_reopen_title),
            label = stringResource(R.string.detail_reopen_reason),
            value = reopenReason,
            onValueChange = { reopenReason = it },
            confirmLabel = stringResource(R.string.detail_reopen_confirm),
            onConfirm = {
                onReopen(reopenReason)
                reopenReason = ""
                showReopenDialog = false
            },
            onDismiss = { showReopenDialog = false }
        )
    }

    if (showInfoDialog) {
        TextInputDialog(
            title = stringResource(if (isOperator) R.string.detail_info_title_operator else R.string.detail_info_title_citizen),
            label = stringResource(if (isOperator) R.string.detail_info_label_operator else R.string.detail_info_label_citizen),
            value = infoText,
            onValueChange = { infoText = it },
            confirmLabel = stringResource(R.string.detail_info_send),
            onConfirm = {
                onRequestInfo(infoText)
                infoText = ""
                showInfoDialog = false
            },
            onDismiss = { showInfoDialog = false }
        )
    }

    // Anónimo: mapa com zoom afastado e sem morada exata (enunciado, I.1.C)
    val mapContent: @Composable (Modifier) -> Unit = { mapModifier ->
        if (incident.latitude != 0.0 || incident.longitude != 0.0) {
            Card(modifier = mapModifier) {
                IncidentMap(
                    latitude = incident.latitude,
                    longitude = incident.longitude,
                    zoom = if (isAnonymous) 13.0 else 17.0,
                    markerTitle = incident.title,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    val detailsContent: LazyListScope.(Boolean) -> Unit = { includeMap ->
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(incident.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                StatusChip(incident.status)
            }
            Text(incident.description)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            IncidentPhotoGallery(media = incident.media, pendingCount = incident.mediaPendingCount)
        }

        if (incident.status == IncidentStatus.INFORMATION_REQUEST) {
            item {
                val byOperator = incident.infoRequestedByRole == UserRole.OPERATOR.name
                val waitingForMe = (byOperator && !isOperator) || (!byOperator && isOperator)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Text(
                        text = stringResource(if (byOperator) R.string.detail_info_by_operator else R.string.detail_info_by_citizen) + " " +
                            stringResource(if (waitingForMe) R.string.detail_info_reply_below else R.string.detail_info_waiting),
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            IncidentInfoBlock(incident = incident, isAnonymous = isAnonymous, isOperator = isOperator)
        }

        if (includeMap) {
            item { mapContent(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))) }
        }

        if (!isAnonymous && (incident.latitude != 0.0 || incident.longitude != 0.0)) {
            item { NavigateButton(incident) }
        }

        if (incident.status in listOf(IncidentStatus.RESOLVED, IncidentStatus.CLOSED) &&
            (incident.resolutionProofUrl.isNotBlank() || incident.proofPending)
        ) {
            item {
                Text(stringResource(R.string.detail_resolution_proof), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                IncidentPhotoGallery(
                    media = listOfNotNull(
                        incident.resolutionProofUrl.takeIf { it.isNotBlank() }?.let {
                            MediaAttachment(it, incident.resolutionProofThumbUrl, incident.resolutionProofIsVideo)
                        }
                    ),
                    pendingCount = if (incident.proofPending) 1 else 0
                )
            }
        }

        if (!isAnonymous && !hasSupported && !isClosed) {
            item {
                Button(onClick = onSupport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.ThumbUp, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(pluralStringResource(R.plurals.detail_support, incident.supporterIds.size, incident.supporterIds.size))
                }
            }
        }

        if (isOperator) {
            item {
                OperatorActions(
                    incident = incident,
                    loading = loading,
                    proofSelected = proofUri != null,
                    onChangeStatus = onChangeStatus,
                    onRegisterWorkStart = onRegisterWorkStart,
                    onRequestInfo = { showInfoDialog = true },
                    onPickProof = { proofPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onResolve = {
                        proofUri?.let(onResolveWithProof)
                        proofUri = null
                    }
                )
            }
        }

        if (canAskClarification) {
            item {
                OutlinedButton(onClick = { showInfoDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.detail_info_title_citizen))
                }
            }
        }

        if (incident.status == IncidentStatus.RESOLVED && canReopen) {
            item {
                OutlinedButton(
                    onClick = { showReopenDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.detail_reopen_button))
                }
            }
        }

        if (!isAnonymous && incident.history.isNotEmpty()) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.detail_history), style = MaterialTheme.typography.titleMedium)
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

        if (!isAnonymous) {
            item {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.messages_section_title), style = MaterialTheme.typography.titleMedium)
            }
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
            item {
                if (isClosed) {
                    Text(stringResource(R.string.detail_closed_no_messages), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.send_message_placeholder)) }
                        )
                        Spacer(Modifier.size(8.dp))
                        Button(
                            enabled = messageText.isNotBlank(),
                            onClick = {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        ) { Text(stringResource(R.string.btn_send)) }
                    }
                }
            }
        }
    }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(
            modifier = modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) { detailsContent(false) }
            mapContent(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            detailsContent(true)
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun IncidentInfoBlock(incident: Incident, isAnonymous: Boolean, isOperator: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(R.string.detail_category, incident.categoryName.ifBlank { stringResource(R.string.category_general) }))
        Text(stringResource(R.string.detail_situation, incidentSituationLabel(incident.situation)))
        Text(stringResource(R.string.detail_status, incidentStatusLabel(incident.status)))
        if (!isAnonymous) {
            Text(stringResource(R.string.detail_author, incident.authorName))
            if (isOperator && incident.authorPhone.isNotBlank()) Text(stringResource(R.string.detail_author_contact, incident.authorPhone))
            Text(stringResource(R.string.detail_location, incident.locationAddress.ifBlank { "%.5f, %.5f".format(incident.latitude, incident.longitude) }))
        } else {
            Text(stringResource(R.string.detail_anonymous_hidden), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (incident.reopenReason.isNotBlank()) {
            Text(stringResource(R.string.detail_reopen_reason_value, incident.reopenReason), color = MaterialTheme.colorScheme.error)
        }
        if (incident.status == IncidentStatus.RESOLVED && incident.resolvedAt != null) {
            Text(stringResource(R.string.detail_auto_close, remainingUntilClose(incident.resolvedAt)), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Intent implícito para navegação no Google Maps (P10, Challenge II). */
@Composable
private fun NavigateButton(incident: Incident) {
    val context = LocalContext.current
    OutlinedButton(onClick = {
        val uri = Uri.parse("geo:${incident.latitude},${incident.longitude}?q=${incident.latitude},${incident.longitude}(${Uri.encode(incident.title)})")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }) {
        Text(stringResource(R.string.detail_navigate))
    }
}

@Composable
private fun OperatorActions(
    incident: Incident,
    loading: Boolean,
    proofSelected: Boolean,
    onChangeStatus: (IncidentStatus) -> Unit,
    onRegisterWorkStart: () -> Unit,
    onRequestInfo: () -> Unit,
    onPickProof: () -> Unit,
    onResolve: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.detail_operator_title), style = MaterialTheme.typography.titleMedium)
        when (incident.status) {
            IncidentStatus.OPEN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRequestInfo) { Text(stringResource(R.string.detail_request_info)) }
                Button(onClick = { onChangeStatus(IncidentStatus.IN_RESOLUTION) }) { Text(stringResource(R.string.detail_start_resolution)) }
            }
            IncidentStatus.INFORMATION_REQUEST ->
                Button(onClick = { onChangeStatus(IncidentStatus.IN_RESOLUTION) }) { Text(stringResource(R.string.detail_start_resolution)) }
            IncidentStatus.IN_RESOLUTION -> {
                incident.workStartedAt?.let { Text(stringResource(R.string.detail_work_started, formatDate(it))) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (incident.workStartedAt == null) {
                        Button(onClick = onRegisterWorkStart) { Text(stringResource(R.string.detail_register_work)) }
                    }
                    OutlinedButton(onClick = onRequestInfo) { Text(stringResource(R.string.detail_request_info)) }
                }
                OutlinedButton(onClick = onPickProof) {
                    Text(stringResource(if (proofSelected) R.string.detail_proof_selected else R.string.detail_attach_proof))
                }
                Button(onClick = onResolve, enabled = proofSelected && !loading) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.detail_mark_resolved))
                }
            }
            IncidentStatus.RESOLVED -> Text(stringResource(R.string.detail_waiting_reopen))
            IncidentStatus.CLOSED -> Text(stringResource(R.string.detail_closed_final), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MessageBubble(msg: IncidentMessage) {
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

@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text(label) }
            )
        },
        confirmButton = { Button(enabled = value.isNotBlank(), onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
private fun remainingUntilClose(resolvedAt: Long): String {
    val remaining = (resolvedAt + BusinessRules.REOPEN_WINDOW_MS - System.currentTimeMillis()).coerceAtLeast(0)
    val hours = (remaining / (60 * 60 * 1000)).toInt()
    return if (hours >= 24) stringResource(R.string.time_days_hours, hours / 24, hours % 24)
    else stringResource(R.string.time_hours, hours)
}
