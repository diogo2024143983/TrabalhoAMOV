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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory
import pt.isec.amov.cityfix.ui.components.IncidentCard

/** Painel de gestão operacional (sem estado de negócio). */
@Composable
fun OperatorScreen(
    activeIncidents: List<Incident>,
    pendingCitizens: List<CityFixUser>,
    categories: List<IncidentCategory>,
    onRefreshPending: () -> Unit,
    onValidateCitizen: (String, Boolean) -> Unit,
    onAddCategory: (String) -> Unit,
    onToggleCategory: (IncidentCategory) -> Unit,
    onRenameCategory: (IncidentCategory, String) -> Unit,
    onIncidentClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newCategoryName by rememberSaveable { mutableStateOf("") }
    var editingCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) { onRefreshPending() }

    categories.firstOrNull { it.id == editingCategoryId }?.let { category ->
        AlertDialog(
            onDismissRequest = { editingCategoryId = null },
            title = { Text("Editar categoria") },
            text = { OutlinedTextField(value = editingName, onValueChange = { editingName = it }, label = { Text("Nome") }) },
            confirmButton = {
                Button(enabled = editingName.isNotBlank(), onClick = {
                    onRenameCategory(category, editingName)
                    editingCategoryId = null
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editingCategoryId = null }) { Text("Cancelar") } }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Painel de Gestão Operacional", style = MaterialTheme.typography.headlineSmall) }

        item { Text("Munícipes Pendentes de Validação (${pendingCitizens.size})", style = MaterialTheme.typography.titleMedium) }
        if (pendingCitizens.isEmpty()) {
            item { Text("Nenhum munícipe pendente de validação.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(pendingCitizens, key = { it.id }) { citizen ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nome: ${citizen.name}", fontWeight = FontWeight.Bold)
                        Text("NIF: ${citizen.nif}  •  Tel: ${citizen.phone}")
                        Text("Georreferenciação: ${citizen.georeference.ifBlank { "Não indicada" }}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onValidateCitizen(citizen.id, true) }) { Text("Ativar Conta") }
                            OutlinedButton(onClick = { onValidateCitizen(citizen.id, false) }) { Text("Manter Pendente") }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Tipologias de Problemas", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Nova categoria") }
                )
                Spacer(Modifier.size(8.dp))
                Button(enabled = newCategoryName.isNotBlank(), onClick = {
                    onAddCategory(newCategoryName)
                    newCategoryName = ""
                }) { Text("Adicionar") }
            }
        }
        items(categories, key = { it.id }) { category ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(category.name, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    editingName = category.name
                    editingCategoryId = category.id
                }) { Icon(Icons.Default.Edit, contentDescription = "Editar ${category.name}") }
                Switch(checked = category.active, onCheckedChange = { onToggleCategory(category) })
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Ocorrências Ativas (${activeIncidents.size})", style = MaterialTheme.typography.titleMedium)
        }
        items(activeIncidents, key = { it.id }) { incident ->
            IncidentCard(incident = incident, onClick = { onIncidentClick(incident.id) })
        }
    }
}
