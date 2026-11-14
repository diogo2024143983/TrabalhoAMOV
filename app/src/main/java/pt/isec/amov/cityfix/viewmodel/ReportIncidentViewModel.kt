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
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private val _formState = MutableStateFlow(ReportIncidentUiState())
    val formState: StateFlow<ReportIncidentUiState> = _formState.asStateFlow()

    fun onTitleChanged(value: String) = _formState.update {
        it.copy(title = value, titleError = if (value.isBlank()) "O título é obrigatório" else null)
    }

    fun onDescriptionChanged(value: String) = _formState.update {
        it.copy(description = value, descriptionError = if (value.isBlank()) "A descrição é obrigatória" else null)
    }

    fun onCategorySelected(category: IncidentCategory) = _formState.update { it.copy(category = category, categoryError = null) }

    fun onSituationSelected(situation: IncidentSituation) = _formState.update { it.copy(situation = situation) }

    fun onPublicChanged(isPublic: Boolean) = _formState.update { it.copy(isPublic = isPublic) }

    fun onAddressChanged(value: String) = _formState.update {
        it.copy(locationAddress = value, addressError = if (value.isBlank()) "A localização é obrigatória" else null)
    }

    /** Localização escolhida no mapa ou obtida pelo GPS (LocationTracker). */
    fun onLocationSelected(latitude: Double, longitude: Double, message: String? = null) = _formState.update {
        it.copy(
            latitude = latitude,
            longitude = longitude,
            locationAddress = it.locationAddress.ifBlank { "%.5f, %.5f".format(latitude, longitude) },
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
        it.copy(attachments = remaining, attachmentsError = if (remaining.isEmpty()) "Anexe pelo menos uma foto ou vídeo" else null)
    }

    fun createCaptureUri(): Uri = mediaRepository.createCaptureUri()

    /** Valida todos os campos de uma vez (ex.: ao carregar em Submeter sem ter tocado em nada). */
    private fun validateAll(): Boolean {
        _formState.update {
            it.copy(
                titleError = if (it.title.isBlank()) "O título é obrigatório" else null,
                descriptionError = if (it.description.isBlank()) "A descrição é obrigatória" else null,
                categoryError = if (it.category == null) "Escolha uma categoria" else null,
                addressError = if (it.locationAddress.isBlank()) "A localização é obrigatória" else null,
                attachmentsError = if (it.attachments.isEmpty()) "Anexe pelo menos uma foto ou vídeo" else null
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
                onSuccess = { onResult(true, "Já existia uma ocorrência igual a menos de 10 m. Foste adicionado como apoiante.") },
                onError = { e -> onResult(false, e.localizedMessage ?: "Erro ao apoiar a ocorrência.") }
            )
            return
        }

        _formState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val prepared = form.attachments.mapNotNull { mediaRepository.prepare(Uri.parse(it), "ticket") }
            if (prepared.isEmpty()) {
                _formState.update { it.copy(isSaving = false, attachmentsError = "Não foi possível processar os ficheiros.") }
                return@launch
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
                mediaPendingCount = prepared.size,
                createdAt = now,
                history = listOf(StatusChange(IncidentStatus.OPEN, now, authorId, user.name, user.role, "Ocorrência submetida")),
                supporterIds = listOf(authorId)
            )
            cloudRepository.createTicket(incident)
            prepared.forEachIndexed { index, media -> mediaRepository.enqueueTicketMedia(ticketId, index, media) }

            _formState.update { it.copy(isSaving = false) }
            onResult(true, "Ocorrência registada! A multimédia será enviada em segundo plano.")
        }
    }
}
