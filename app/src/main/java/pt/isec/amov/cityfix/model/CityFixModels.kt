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

@Serializable
data class Incident(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val categoryId: String = "",
    val categoryName: String = "",
    val situation: IncidentSituation = IncidentSituation.DAMAGE,
    val status: IncidentStatus = IncidentStatus.OPEN,
    val isPublic: Boolean = true,                    // Bate certo com 'isPublic' da regra Firebase
    val authorId: String = "",                       // Bate certo com 'authorId' da regra Firebase
    val authorName: String = "",
    val authorPhone: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val locationAddress: String = "",
    val mediaUrl: String = "",
    val thumbnailUrl: String = "",
    val isVideo: Boolean = false,
    val resolutionProofUrl: String = "",
    val reopenReason: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val inResolutionAt: Long? = null,
    val resolvedAt: Long? = null,
    val closedAt: Long? = null,
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
