package pt.isec.amov.cityfix.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.isec.amov.cityfix.data.MediaRepository
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.model.IncidentList
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.StatusChange
import java.util.UUID
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.utils.Strings
import pt.isec.amov.cityfix.model.MediaAttachment
import java.io.File

/**
 * Estado imutável do formulário de nova ocorrência (FixIt Project Bridge, P08):
 * título, descrição, categoria e erros de validação num único objeto com isValid calculado.
 */
data class ReportIncidentUiState(
    val title: String = "",
    val description: String = "",
    val category: IncidentCategory? = null,
    val situation: IncidentSituation = IncidentSituation.DAMAGE,
    val isPublic: Boolean = true,
    val latitude: Double = BusinessRules.PRACA_REPUBLICA_LAT,
    val longitude: Double = BusinessRules.PRACA_REPUBLICA_LON,
    val locationAddress: String = "",
    val attachments: List<String> = emptyList(),
    val titleError: String? = null,
    val descriptionError: String? = null,
    val categoryError: String? = null,
    val addressError: String? = null,
    val attachmentsError: String? = null,
    val locationMessage: String? = null,
    val isSaving: Boolean = false
) {
    val canAddMoreAttachments: Boolean get() = attachments.size < BusinessRules.MAX_ATTACHMENTS

    val isValid: Boolean
        get() = titleError == null && descriptionError == null && categoryError == null && addressError == null &&
            title.isNotBlank() && description.isNotBlank() && category != null &&
            locationAddress.isNotBlank() && attachments.isNotEmpty()
}

class ReportIncidentViewModel(
    private val cloudRepository: CityFixFirebaseRepository,
    private val mediaRepository: MediaRepository,
    private val strings: Strings = Strings { id, _ -> id.toString() }
) : ViewModel() {
    private val _formState = MutableStateFlow(ReportIncidentUiState())
    val formState: StateFlow<ReportIncidentUiState> = _formState.asStateFlow()

    fun onTitleChanged(value: String) = _formState.update {
        it.copy(title = value, titleError = if (value.isBlank()) strings.get(R.string.err_title_required) else null)
    }

    fun onDescriptionChanged(value: String) = _formState.update {
        it.copy(description = value, descriptionError = if (value.isBlank()) strings.get(R.string.err_description_required) else null)
    }

    fun onCategorySelected(category: IncidentCategory) = _formState.update { it.copy(category = category, categoryError = null) }

    fun onSituationSelected(situation: IncidentSituation) = _formState.update { it.copy(situation = situation) }

    fun onPublicChanged(isPublic: Boolean) = _formState.update { it.copy(isPublic = isPublic) }

    /** Pré-preenchimento após um impacto detetado pelo acelerómetro (I.2.F): Vias Públicas / Dano. */
    fun prefillFromImpact(categories: List<IncidentCategory>, magnitude: Float?) = _formState.update { form ->
        if (form.title.isNotBlank()) return@update form
        val roads = categories.firstOrNull { it.id == "vias" } ?: categories.firstOrNull { it.name.contains("Vias", ignoreCase = true) }
        form.copy(
            title = strings.get(R.string.impact_prefill_title),
            description = strings.get(R.string.impact_prefill_description, magnitude ?: 0f),
            category = roads ?: form.category,
            situation = IncidentSituation.DAMAGE,
            titleError = null,
            descriptionError = null,
            categoryError = null
        )
    }

    fun onAddressChanged(value: String) = _formState.update {
        it.copy(locationAddress = value, addressError = if (value.isBlank()) strings.get(R.string.err_location_required) else null)
    }

    /** Localização escolhida no mapa ou obtida pelo GPS (LocationTracker). */
    fun onLocationSelected(latitude: Double, longitude: Double, message: String? = null) = _formState.update {
        it.copy(
            latitude = latitude,
            longitude = longitude,
            locationAddress = it.locationAddress.ifBlank { // Locale.ROOT: ponto decimal, para não confundir com a vírgula que separa latitude e longitude
                String.format(java.util.Locale.ROOT, "%.5f, %.5f", latitude, longitude) },
            addressError = null,
            locationMessage = message
        )
    }

    fun onLocationMessage(message: String?) = _formState.update { it.copy(locationMessage = message) }

    fun onAttachmentsAdded(uris: List<Uri>) = _formState.update {
        val merged = (it.attachments + uris.map(Uri::toString)).distinct().take(BusinessRules.MAX_ATTACHMENTS)
        it.copy(attachments = merged, attachmentsError = null)
    }

    fun onAttachmentRemoved(uri: String) = _formState.update {
        val remaining = it.attachments - uri
        it.copy(attachments = remaining, attachmentsError = if (remaining.isEmpty()) strings.get(R.string.err_attachment_required) else null)
    }

    fun createCaptureUri(): Uri = mediaRepository.createCaptureUri()

    /** Valida todos os campos de uma vez (ex.: ao carregar em Submeter sem ter tocado em nada). */
    private fun validateAll(): Boolean {
        _formState.update {
            it.copy(
                titleError = if (it.title.isBlank()) strings.get(R.string.err_title_required) else null,
                descriptionError = if (it.description.isBlank()) strings.get(R.string.err_description_required) else null,
                categoryError = if (it.category == null) strings.get(R.string.err_category_required) else null,
                addressError = if (it.locationAddress.isBlank()) strings.get(R.string.err_location_required) else null,
                attachmentsError = if (it.attachments.isEmpty()) strings.get(R.string.err_attachment_required) else null
            )
        }
        return _formState.value.isValid
    }

    /**
     * Submete a ocorrência. Se já existir uma igual a menos de 10 m, o utilizador passa a apoiante
     * (ocorrência de grupo). Caso contrário o ticket é gravado (funciona offline) e a multimédia
     * é comprimida e enviada em segundo plano pelo WorkManager.
     */
    fun submit(user: CityFixUser, existing: IncidentList, onResult: (Boolean, String) -> Unit) {
        if (!validateAll()) return
        val form = _formState.value
        val category = form.category ?: return
        val authorId = cloudRepository.currentUserId ?: user.id

        existing.findDuplicate(category.id, form.situation, form.latitude, form.longitude)?.let { duplicate ->
            cloudRepository.addSupporter(
                ticketId = duplicate.id,
                userId = authorId,
                onSuccess = { onResult(true, strings.get(R.string.report_duplicate_supported)) },
                onError = { _ -> onResult(false, strings.get(R.string.err_support_failed)) }
            )
            return
        }

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val prepared = form.attachments.mapNotNull { mediaRepository.prepare(Uri.parse(it), "ticket") }
            if (prepared.isEmpty()) {
                _formState.update { it.copy(isSaving = false, attachmentsError = strings.get(R.string.err_media_processing)) }
                return@launch
            }

            val localAttachments = prepared.map { item ->
                MediaAttachment(
                    url = Uri.fromFile(File(item.mediaPath)).toString(),
                    thumbnailUrl = item.thumbnailPath?.let { Uri.fromFile(File(it)).toString() }.orEmpty(),
                    isVideo = item.isVideo
                )
            }

            val now = System.currentTimeMillis()
            val ticketId = UUID.randomUUID().toString()
            val incident = Incident(
                id = ticketId,
                title = form.title.trim(),
                description = form.description.trim(),
                categoryId = category.id,
                categoryName = category.name,
                situation = form.situation,
                status = IncidentStatus.OPEN,
                isPublic = form.isPublic,
                authorId = authorId,
                authorName = user.name,
                authorPhone = user.phone,
                latitude = form.latitude,
                longitude = form.longitude,
                locationAddress = form.locationAddress.trim(),
                media = localAttachments,
                mediaPendingCount = 0,
                createdAt = now,
                history = listOf(StatusChange(IncidentStatus.OPEN, now, authorId, user.name, user.role, strings.get(R.string.history_submitted))),
                supporterIds = listOf(authorId)
            )
            cloudRepository.createTicket(incident)
            prepared.forEachIndexed { index, media -> mediaRepository.enqueueTicketMedia(ticketId, index, media) }

            _formState.update { it.copy(isSaving = false) }
            onResult(true, strings.get(R.string.report_submitted))
        }
    }
}
