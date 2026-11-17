package pt.isec.amov.cityfix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.IncidentList
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import java.util.Locale

/** Dashboard sem estado: tempo médio de reparação, total e % de ocorrências ativas (enunciado, I.1.A). */
@Composable
fun DashboardScreen(
    user: CityFixUser,
    incidents: IncidentList,
    onIncidents: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = MaterialTheme.shapes.large
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (user.role == UserRole.ANONYMOUS)
                            stringResource(R.string.dash_hero_anon_title)
                        else
                            stringResource(R.string.dash_hero_user_title, user.name),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = when {
                            user.role == UserRole.ANONYMOUS -> stringResource(R.string.dash_hero_anon_sub)
                            user.role == UserRole.CITIZEN && !user.active -> stringResource(R.string.account_pending_warning)
                            else -> stringResource(R.string.dash_hero_user_sub)
                        },
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = stringResource(R.string.dash_total_tickets),
                    value = incidents.totalCount.toString(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.dash_active_percentage),
                    value = "${incidents.activePercentage}% (${incidents.activeCount})",
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = stringResource(R.string.dash_avg_resolution_time),
                    value = incidents.averageRepairDays.takeIf { it > 0.0 }?.let { String.format(Locale.getDefault(), "%.1f d", it) } ?: "--",
                    color = Color(0xFFF4E4C8),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.dash_community_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.dash_community_sub),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onIncidents) { Text(stringResource(R.string.btn_explore_incidents)) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color), shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DashboardScreenPreview() {
    CityFixTheme {
        DashboardScreen(user = CityFixUser(name = "Ana", role = UserRole.CITIZEN), incidents = IncidentList(), onIncidents = {})
    }
}