package pt.isec.amov.cityfix.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.isec.amov.cityfix.model.CityFixUser
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentCategory

@Composable
fun OperatorScreen(
    incidents: List<Incident>,
    pendingCitizens: List<CityFixUser>,
    categories: List<IncidentCategory>,
    onRefreshPending: () -> Unit,
    onValidateCitizen: (String, Boolean) -> Unit,
    onSaveCategory: (String, Boolean) -> Unit,
    onSelectIncident: (Incident) -> Unit
) {
    var newCategoryName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onRefreshPending()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Painel de Gestão Operacional", style = MaterialTheme.typography.headlineSmall)
        }

        item {
            Text(
                "Munícipes Pendentes de Validação (${pendingCitizens.size})",
                style = MaterialTheme.typography.titleMedium
            )
        }

        if (pendingCitizens.isEmpty()) {
            item {
                Text("Nenhum munícipe pendente de validação.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(pendingCitizens, key = { it.id }) { citizen ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nome: ${citizen.name}", fontWeight = FontWeight.Bold)
                        Text("NIF: ${citizen.nif}  •  Tel: ${citizen.phone}")
                        Text("Georreferenciação: ${citizen.georeference.ifBlank { "Não indicada" }}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onValidateCitizen(citizen.id, true) }) {
                                Text("Ativar Conta")
                            }
                            OutlinedButton(onClick = { onValidateCitizen(citizen.id, false) }) {
                                Text("Manter Pendente")
                            }
                        }
                    }
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Configuração de Categorias", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Nova Categoria (ex: Água, Iluminação)") }
                )
                Spacer(Modifier.size(8.dp))
                Button(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        onSaveCategory(newCategoryName, true)
                        newCategoryName = ""
                    }
                }) {
                    Text("Adicionar")
                }
            }
        }

        if (categories.isNotEmpty()) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = cat.active,
                            onClick = { onSaveCategory(cat.name, !cat.active) },
                            label = { Text("${cat.name} (${if (cat.active) "Ativa" else "Inativa"})") }
                        )
                    }
                }
            }
        }

        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Ocorrências Ativas (${incidents.size})", style = MaterialTheme.typography.titleMedium)
        }

        items(incidents, key = { it.id }) { incident ->
            IncidentCard(incident, onSelectIncident)
        }
    }
}