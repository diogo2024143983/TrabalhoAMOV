package pt.isec.amov.cityfix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.ui.components.IncidentCard
import pt.isec.amov.cityfix.ui.theme.CityFixTheme

/** Lista virtualizada e sem estado das ocorrências (padrão ListScreen de P07). */
@Composable
fun IncidentListScreen(
    incidents: List<Incident>,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (incidents.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ainda não existem ocorrências registadas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text("Ocorrências Registadas", style = MaterialTheme.typography.headlineSmall)
            Text("Lista de problemas reportados pela comunidade", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(items = incidents, key = { it.id }) { incident ->
            IncidentCard(incident = incident, onClick = { onIncidentClick(incident.id) })
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun IncidentListScreenEmptyPreview() {
    CityFixTheme { IncidentListScreen(incidents = emptyList(), onIncidentClick = {}) }
}
