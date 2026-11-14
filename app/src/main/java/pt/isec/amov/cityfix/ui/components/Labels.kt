package pt.isec.amov.cityfix.ui.components

import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

fun roleLabel(role: UserRole): String = when (role) {
    UserRole.OPERATOR -> "Operador Municipal"
    UserRole.CITIZEN -> "Munícipe"
    UserRole.ANONYMOUS -> "Cidadão Anónimo"
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(millis))
