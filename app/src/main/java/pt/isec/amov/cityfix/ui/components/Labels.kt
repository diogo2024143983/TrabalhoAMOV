package pt.isec.amov.cityfix.ui.components

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Rótulos traduzidos (values = inglês por omissão, values-pt = português).
// As variantes @StringRes servem para código sem Compose (notificações, serviços).

@StringRes
fun situationRes(situation: IncidentSituation): Int = when (situation) {
    IncidentSituation.DAMAGE -> R.string.situation_damage
    IncidentSituation.BREAKDOWN -> R.string.situation_breakdown
    IncidentSituation.DESTRUCTION -> R.string.situation_destruction
    IncidentSituation.THEFT -> R.string.situation_theft
}

@StringRes
fun statusRes(status: IncidentStatus): Int = when (status) {
    IncidentStatus.OPEN -> R.string.status_open
    IncidentStatus.INFORMATION_REQUEST -> R.string.status_info_request
    IncidentStatus.IN_RESOLUTION -> R.string.status_in_resolution
    IncidentStatus.RESOLVED -> R.string.status_resolved
    IncidentStatus.CLOSED -> R.string.status_closed
}

@StringRes
fun roleRes(role: UserRole): Int = when (role) {
    UserRole.OPERATOR -> R.string.role_operator
    UserRole.CITIZEN -> R.string.role_citizen
    UserRole.ANONYMOUS -> R.string.role_anonymous
}

@Composable
fun incidentSituationLabel(situation: IncidentSituation): String = stringResource(situationRes(situation))

@Composable
fun incidentStatusLabel(status: IncidentStatus): String = stringResource(statusRes(status))

@Composable
fun roleLabel(role: UserRole): String = stringResource(roleRes(role))

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
