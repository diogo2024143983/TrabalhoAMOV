package pt.isec.amov.cityfix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils

@Composable
fun DashboardScreen(
    incidents: List<Incident>,
    user: CityFixUser,
    onIncidents: () -> Unit,
    onNew: () -> Unit
) {
    val avgRepairDays = remember(incidents) {
        CityFixUtils.calculateAverageRepairTimeDays(incidents)
    }

    val totalCount = incidents.size
    val activeCount = incidents.count { it.status != IncidentStatus.CLOSED && it.status != IncidentStatus.RESOLVED }
    val activePercentage = if (totalCount > 0) (activeCount * 100) / totalCount else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                        text = if (user.role == UserRole.ANONYMOUS) "Melhore a sua cidade" else "Olá, ${user.name}!",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = if (user.role == UserRole.ANONYMOUS)
                            "Consulte o estado de resolução das ocorrências na sua cidade."
                        else if (!user.active && user.role == UserRole.CITIZEN)
                            "A tua conta está a aguardar validação do NIF por um operador municipal."
                        else
                            "Reporte ocorrências públicas ou acompanhe a evolução das que já registou.",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                }
            }
        }

        // Estatísticas exigidas na Alínea 1.A (Total, % Ativas e Tempo Médio de Reparação)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard(
                    label = "Total",
                    value = totalCount.toString(),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "% Ativas",
                    value = "$activePercentage% ($activeCount)",
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Tempo Médio",
                    value = if (avgRepairDays > 0.0) String.format("%.1f d", avgRepairDays) else "--",
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
                    Text("Ocorrências da Comunidade", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Consulte os problemas ativos e acompanhe a resolução em tempo real.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onIncidents) {
                        Text("Explorar ocorrências")
                    }
                }
            }
        }

        if (user.role != UserRole.ANONYMOUS && (user.role == UserRole.OPERATOR || user.active)) {
            item {
                OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Registar nova ocorrência")
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