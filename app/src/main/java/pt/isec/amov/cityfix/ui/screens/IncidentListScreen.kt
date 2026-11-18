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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.R
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
                        stringResource(R.string.geofence_out_of_bounds_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        stringResource(R.string.geofence_out_of_bounds_message),
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
            Text(stringResource(R.string.list_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.list_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (currentUser.role == UserRole.CITIZEN) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !onlyMine,
                        onClick = { onlyMine = false },
                        label = { Text(stringResource(R.string.list_filter_public)) }
                    )
                    FilterChip(
                        selected = onlyMine,
                        onClick = { onlyMine = true },
                        label = { Text(stringResource(R.string.nav_my_tickets)) }
                    )
                }
            }
        }
        if (filteredIncidents.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.list_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
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