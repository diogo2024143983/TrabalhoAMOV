package pt.isec.amov.cityfix.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole { ANONYMOUS, CITIZEN, OPERATOR }

@Serializable
enum class IncidentStatus { OPEN, INFORMATION_REQUEST, IN_RESOLUTION, RESOLVED, CLOSED }

@Serializable
enum class IncidentSituation { DAMAGE, BREAKDOWN, DESTRUCTION, THEFT }


@Serializable
data class IncidentCategory(
    val id: String = "",
    val name: String = "",
    val active: Boolean = true
)
@Serializable
data class CityFixUser(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val nif: String = "",
    val role: UserRole = UserRole.ANONYMOUS,
    val active: Boolean = true,
    val georeference: String = "",
    val photoUrl: String? = null,
    val impactDetectionEnabled: Boolean = false,
    val messagingToken: String = ""
)

/** Entrada do histórico do ticket: cada transição de estado fica registada com timestamp. */
@Serializable
data class StatusChange(
    val status: IncidentStatus = IncidentStatus.OPEN,
    val at: Long = System.currentTimeMillis(),
    val byUid: String = "",
    val byName: String = "",
    val byRole: UserRole = UserRole.CITIZEN,
    val note: String = ""
)

@Serializable
data class Incident(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val situation: IncidentSituation = IncidentSituation.DAMAGE,
    val status: IncidentStatus = IncidentStatus.OPEN,
    val isPublic: Boolean = true,
    val authorId: String = "",
    val authorName: String = "",
    val authorPhone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationAddress: String = "",
    // Multimédia da ocorrência (preenchida pelo MediaUploadWorker)
    val mediaUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val mediaPending: Boolean = false,
    // Prova de resolução (foto/vídeo carregado pelo operador)
    val resolutionProofUrl: String = "",
    val resolutionProofThumbUrl: String = "",
    val resolutionProofIsVideo: Boolean = false,
    val proofPending: Boolean = false,
    val reopenReason: String = "",
    // Timestamps do ciclo de vida
    val createdAt: Long = System.currentTimeMillis(),
    val inResolutionAt: Long? = null,
    val workStartedAt: Long? = null,
    val resolvedAt: Long? = null,
    val closedAt: Long? = null,
    val history: List<StatusChange> = emptyList(),
    // Pedido de informações (em qualquer dos sentidos)
    val infoRequestedByRole: String = "",
    val statusBeforeInfoRequest: String = "",
    // Resumo da última mensagem (usado para notificações)
    val lastMessageAt: Long = 0L,
    val lastMessageAuthorId: String = "",
    val lastMessageText: String = "",
    val supporterIds: List<String> = emptyList()
)

@Serializable
data class IncidentMessage(
    val id: String = "",
    val ticketId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorRole: UserRole = UserRole.CITIZEN,
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** Ficheiros locais já comprimidos, prontos a serem enviados pelo WorkManager. */
data class PreparedMedia(
    val mediaPath: String,
    val thumbnailPath: String?,
    val isVideo: Boolean
)
