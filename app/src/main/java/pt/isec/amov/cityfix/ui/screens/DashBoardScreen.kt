package pt.isec.amov.cityfix.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.IncidentList
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.components.DashboardIncidentsMap
import pt.isec.amov.cityfix.ui.theme.CityFixTheme

@Composable
fun DashboardScreen(
    user: CityFixUser,
    incidents: IncidentList,
    onIncidents: () -> Unit,
    modifier: Modifier = Modifier,
    onIncidentClick: (String) -> Unit = {},
    /** true quando o botão flutuante "+" está visível: o botão da lista deixa-lhe espaço. */
    reserveFabSpace: Boolean = false
) {
    val isAnonymous = user.role == UserRole.ANONYMOUS
    val visibleIncidents = incidents.visibleTo(user)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Cabeçalho compacto (sem banner gigante)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (isAnonymous)
                    stringResource(R.string.dash_improve_city)
                else
                    stringResource(R.string.dash_hello, user.name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            if (user.role == UserRole.CITIZEN && !user.active) {
                Text(
                    text = stringResource(R.string.account_pending_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        // Barra única de estatísticas com divisores verticais
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatColumn(
                    value = incidents.totalCount.toString(),
                    label = stringResource(R.string.dash_total_tickets),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                StatColumn(
                    value = "${incidents.activePercentage}%",
                    label = stringResource(R.string.dash_active_percentage),
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                StatColumn(
                    value = incidents.averageRepairDays.takeIf { it > 0.0 }?.let { days ->
                        // Menos de um dia: mostrar em horas (evita "0,0 dias")
                        if (days < 1.0) stringResource(R.string.dash_hours_format, days * 24) else stringResource(R.string.dash_days_format, days)
                    } ?: "--",
                    label = stringResource(R.string.dash_avg_resolution_time),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Mapa em destaque a ocupar o restante espaço do ecrã
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            DashboardIncidentsMap(
                incidents = visibleIncidents,
                isAnonymous = isAnonymous,
                onIncidentClick = onIncidentClick,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp))
            )
        }

        // Botão limpo para abrir a lista completa (sem partir texto)
        OutlinedButton(
            onClick = onIncidents,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (reserveFabSpace) 80.dp else 0.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.dash_explore))
        }
    }
}

@Composable
private fun StatColumn(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    CityFixTheme {
        DashboardScreen(user = CityFixUser(name = "Rodrigo", role = UserRole.CITIZEN), incidents = IncidentList(), onIncidents = {})
    }
}