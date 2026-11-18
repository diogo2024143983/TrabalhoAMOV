package pt.isec.amov.cityfix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.R
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentSituation
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.ui.theme.CityFixTheme
import androidx.compose.foundation.BorderStroke


/** Cartão sem estado de uma ocorrência: recebe dados imutáveis e emite o clique para cima. */
@Composable
fun IncidentCard(
    incident: Incident,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Etiqueta da ação para o leitor de ecrã ("toque duas vezes para abrir detalhes")
            .clickable(onClickLabel = stringResource(R.string.cd_open_incident), role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)

    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumb = incident.coverThumbnail
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (thumb.isNotBlank()) {
                    AsyncImage(
                        model = thumb,
                        contentDescription = stringResource(R.string.cd_thumbnail_of, incident.title),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (incident.media.firstOrNull()?.isVideo == true) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = stringResource(R.string.cd_video),
                            tint = Color.White
                        )
                    }
                } else if (incident.mediaPendingCount > 0) {
                    Icon(Icons.Default.CloudUpload, contentDescription = stringResource(R.string.cd_media_uploading))
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = incident.title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChip(incident.status)
                }
                Text(
                    text = "${incident.categoryName.ifBlank { stringResource(R.string.category_general) }}  •  ${
                        incidentSituationLabel(
                            incident.situation
                        )
                    }",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = incident.description,
                    maxLines = 2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ThumbUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = pluralStringResource(R.plurals.supporters_count, incident.supporterIds.size, incident.supporterIds.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun StatusChip(status: IncidentStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        IncidentStatus.OPEN -> Color(0xFF1E3A5F) to Color(0xFF93C5FD)
        IncidentStatus.INFORMATION_REQUEST -> Color(0xFF45270A) to Color(0xFFFCD34D)
        IncidentStatus.IN_RESOLUTION -> Color(0xFF312E81) to Color(0xFFC4B5FD)
        IncidentStatus.RESOLVED -> Color(0xFF14532D) to Color(0xFF86EFAC)
        IncidentStatus.CLOSED -> Color(0xFF334155) to Color(0xFFCBD5E1)
    }
    Surface(
        modifier = modifier,
        color = bgColor,
        contentColor = textColor,
        shape = RoundedCornerShape(6.dp),
    ) {
        Text(
            text = incidentStatusLabel(status),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IncidentCardPreview() {
    CityFixTheme {
        IncidentCard(
            incident = Incident(
                id = "1",
                title = "Buraco na estrada",
                description = "Buraco profundo junto à passadeira.",
                categoryName = "Vias Públicas",
                situation = IncidentSituation.DAMAGE,
                status = IncidentStatus.IN_RESOLUTION,
                supporterIds = listOf("a", "b")
            ),
            onClick = {}
        )
    }
}
