package pt.isec.amov.cityfix.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole { ANONYMOUS, CITIZEN, OPERATOR }

@Serializable
enum class IncidentStatus { OPEN, INFORMATION_REQUEST, IN_RESOLUTION, RESOLVED, CLOSED }

@Serializable
enum class IncidentSituation { DAMAGE, BREAKDOWN, DESTRUCTION, THEFT }

@Serializable
data class CityFixUser(
    val name: String,
    val email: String = "",
    val nif: String = "",
    val role: UserRole = UserRole.ANONYMOUS,
    val active: Boolean = true
)

@Serializable
data class Incident(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val situation: IncidentSituation,
    val status: IncidentStatus = IncidentStatus.OPEN,
    val public: Boolean = true,
    val author: String,
    val location: String,
    val hasMedia: Boolean = true,
    val supporters: Int = 1
)
