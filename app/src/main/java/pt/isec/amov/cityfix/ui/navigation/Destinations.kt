package pt.isec.amov.cityfix.ui.navigation

import kotlinx.serialization.Serializable

// Rotas type-safe (Navigation 2.8+, P07): data object para rotas sem parâmetros,
// data class para rotas com parâmetros.

@Serializable
data object DashboardRoute

@Serializable
data object IncidentListRoute

@Serializable
data object ReportIncidentRoute

@Serializable
data class IncidentDetailRoute(val incidentId: String)

@Serializable
data object ProfileRoute

@Serializable
data object EditProfileRoute

@Serializable
data object OperatorRoute

@Serializable
data class AuthRoute(val startInRegisterMode: Boolean = false)
