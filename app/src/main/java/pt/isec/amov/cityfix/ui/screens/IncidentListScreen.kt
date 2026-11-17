package pt.isec.amov.cityfix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.ui.components.IncidentCard
import pt.isec.amov.cityfix.ui.theme.CityFixTheme

/** Lista virtualizada e sem estado das ocorrências (padrão ListScreen de P07). */
@Composable
fun IncidentListScreen(
    incidents: List<Incident>,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    currentUser: CityFixUser = CityFixUser(),
    isAnonymousOutOfBounds: Boolean = false
) {
    var onlyMine by rememberSaveable { mutableStateOf(false) }

    if (currentUser.role == UserRole.ANONYMOUS && isAnonymousOutOfBounds) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Fora do Perímetro de 10 km",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        "O acesso de Munícipe Anónimo está restrito a um raio de 10 km com centro na Praça da República (Coimbra). Inicie sessão para consultar ocorrências fora deste perímetro.",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
        return
    }

    val filteredIncidents = if (currentUser.role == UserRole.CITIZEN && onlyMine) {
        incidents.filter { it.authorId == currentUser.id || currentUser.id in it.supporterIds }
    } else if (currentUser.role == UserRole.CITIZEN) {
        incidents.filter { it.isPublic && it.isActive }
    } else {
        incidents
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Ocorrências Registadas", style = MaterialTheme.typography.headlineSmall)
            Text("Lista de problemas reportados pela comunidade", color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (currentUser.role == UserRole.CITIZEN) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !onlyMine,
                        onClick = { onlyMine = false },
                        label = { Text("Públicas Ativas") }
                    )
                    FilterChip(
                        selected = onlyMine,
                        onClick = { onlyMine = true },
                        label = { Text("As Minhas Ocorrências") }
                    )
                }
            }
        }
        if (filteredIncidents.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text("Ainda não existem ocorrências nesta vista.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(items = filteredIncidents, key = { it.id }) { incident ->
                IncidentCard(incident = incident, onClick = { onIncidentClick(incident.id) })
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IncidentListScreenEmptyPreview() {
    CityFixTheme { IncidentListScreen(incidents = emptyList(), onIncidentClick = {}) }
}