package pt.isec.amov.cityfix.ui.screens

import android.Manifest
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.ui.components.AttachmentPreviewRow
import pt.isec.amov.cityfix.ui.components.CategoryDropdown
import pt.isec.amov.cityfix.ui.components.IncidentMap
import pt.isec.amov.cityfix.ui.components.LabeledSwitch
import pt.isec.amov.cityfix.ui.components.incidentSituationLabel
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import pt.isec.amov.cityfix.utils.LocationTracker
import pt.isec.amov.cityfix.viewmodel.ReportIncidentUiState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.text.input.ImeAction

@Composable
fun ReportIncidentScreen(
    state: ReportIncidentUiState,
    categories: List<IncidentCategory>,
    onTitleChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onCategorySelected: (IncidentCategory) -> Unit,
    onSituationSelected: (IncidentSituation) -> Unit,
    onPublicChange: (Boolean) -> Unit,
    onAddressChange: (String) -> Unit,
    onLocationSelected: (Double, Double, String?) -> Unit,
    onLocationMessage: (String?) -> Unit,
    onAttachmentsAdded: (List<Uri>) -> Unit,
    onAttachmentRemoved: (String) -> Unit,
    onCreateCaptureUri: () -> Uri,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val remainingSlots = BusinessRules.MAX_ATTACHMENTS - state.attachments.size
    // Textos usados dentro de callbacks (lidos aqui para acompanharem mudanças de idioma)
    val msgGpsOk = stringResource(R.string.report_gps_ok)
    val msgGpsUnavailable = stringResource(R.string.report_gps_unavailable)
    val msgGpsNoPermission = stringResource(R.string.report_gps_no_permission)
    val msgAddressFound = stringResource(R.string.report_address_found)
    val msgAddressNotFound = stringResource(R.string.report_address_not_found)
    val msgMapPicked = stringResource(R.string.report_map_picked)

    // Galeria: até 3 fotos/vídeos (FixIt Project Bridge, P09)
    val multiplePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = BusinessRules.MAX_ATTACHMENTS)
    ) { uris -> if (uris.isNotEmpty()) onAttachmentsAdded(uris) }
    val singlePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAttachmentsAdded(listOf(uri))
    }

    // Câmara: contrato TakePicture com Uri do FileProvider (P09)
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        if (success && uri != null) onAttachmentsAdded(listOf(uri))
        pendingCaptureUri = null
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = onCreateCaptureUri()
            pendingCaptureUri = uri
            takePicture.launch(uri)
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) {
            scope.launch {
                val loc = LocationTracker.currentLocation(context)
                if (loc != null) onLocationSelected(loc.latitude, loc.longitude, msgGpsOk)
                else onLocationMessage(msgGpsUnavailable)
            }
        } else {
            onLocationMessage(msgGpsNoPermission)
        }
    }

    val formContent: @Composable (Modifier) -> Unit = { formModifier ->
        Column(modifier = formModifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.title_label) + " *") },
                isError = state.titleError != null,
                supportingText = state.titleError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = onDescriptionChange,
                label = { Text(stringResource(R.string.description_label) + " *") },
                isError = state.descriptionError != null,
                supportingText = state.descriptionError?.let { { Text(it) } },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
            CategoryDropdown(
                categories = categories,
                selected = state.category,
                onSelected = onCategorySelected,
                error = state.categoryError,
                modifier = Modifier.fillMaxWidth()
            )
            Text(stringResource(R.string.situation_label), style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IncidentSituation.entries.forEach { sit ->
                    FilterChip(
                        selected = state.situation == sit,
                        onClick = { onSituationSelected(sit) },
                        label = { Text(incidentSituationLabel(sit)) }
                    )
                }
            }
            val searchAddress: () -> Unit = {
                scope.launch {
                    val coords = LocationTracker.geocodeAddress(context, state.locationAddress)
                    if (coords != null) {
                        onLocationSelected(coords.first, coords.second, msgAddressFound)
                    } else {
                        onLocationMessage(msgAddressNotFound)
                    }
                }
            }
            OutlinedTextField(
                value = state.locationAddress,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.location_label) + " *") },
                isError = state.addressError != null,
                supportingText = state.addressError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { searchAddress() }),
                trailingIcon = {
                    IconButton(onClick = searchAddress) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.cd_search_address))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {
                    locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) {
                    Icon(Icons.Default.MyLocation, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.report_use_gps))
                }
                state.locationMessage?.let {
                    Text(it, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
            LabeledSwitch(
                text = stringResource(if (state.isPublic) R.string.scope_public else R.string.scope_private),
                checked = state.isPublic,
                onCheckedChange = onPublicChange
            )

            Text(stringResource(R.string.report_attachments, state.attachments.size, BusinessRules.MAX_ATTACHMENTS), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = remainingSlots > 0,
                    onClick = {
                        val request = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        if (remainingSlots > 1) multiplePicker.launch(request) else singlePicker.launch(request)
                    }
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.choose_gallery))
                }
                OutlinedButton(enabled = remainingSlots > 0, onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.take_photo))
                }
            }
            if (state.attachments.isNotEmpty()) {
                AttachmentPreviewRow(attachments = state.attachments, onRemove = onAttachmentRemoved)
            }
            state.attachmentsError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(onClick = onSubmit, enabled = !state.isSaving, modifier = Modifier.fillMaxWidth()) {
                if (state.isSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.submit_incident))
            }
        }
    }

    val mapContent: @Composable (Modifier) -> Unit = { mapModifier ->
        Column(modifier = mapModifier) {
            Text(stringResource(R.string.report_tap_map), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Card(Modifier.fillMaxSize()) {
                IncidentMap(
                    latitude = state.latitude,
                    longitude = state.longitude,
                    zoom = 16.0,
                    modifier = Modifier.fillMaxSize(),
                    onLocationPicked = { lat, lon -> onLocationSelected(lat, lon, msgMapPicked) }
                )
            }
        }
    }

    // Layout adaptativo (P10): em landscape, formulário e mapa lado a lado (50/50)
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            formContent(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()))
            mapContent(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(modifier = modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            formContent(Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            mapContent(Modifier.fillMaxWidth().height(260.dp))
            Spacer(Modifier.height(80.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ReportIncidentScreenPreview() {
    CityFixTheme {
        ReportIncidentScreen(
            state = ReportIncidentUiState(title = "Candeeiro avariado", titleError = null),
            categories = listOf(IncidentCategory("1", "Iluminação")),
            onTitleChange = {}, onDescriptionChange = {}, onCategorySelected = {}, onSituationSelected = {},
            onPublicChange = {}, onAddressChange = {}, onLocationSelected = { _, _, _ -> }, onLocationMessage = {},
            onAttachmentsAdded = {}, onAttachmentRemoved = {}, onCreateCaptureUri = { Uri.EMPTY }, onSubmit = {}
        )
    }
}
