package pt.isec.amov.cityfix.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pt.isec.amov.cityfix.model.MediaAttachment

/**
 * Galeria de evidências da ocorrência (FixIt Project Bridge, P09) com LazyVerticalStaggeredGrid.
 * Os vídeos mostram a miniatura com um ícone de reprodução e abrem no leitor do sistema.
 */
@Composable
fun IncidentPhotoGallery(
    media: List<MediaAttachment>,
    pendingCount: Int,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (media.isEmpty() && pendingCount > 0) {
        PendingMediaCard(modifier)
        return
    }
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Adaptive(minSize = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalItemSpacing = 8.dp,
        modifier = modifier.fillMaxWidth().heightIn(max = 240.dp)
    ) {
        items(media) { item ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .aspectRatio(1f)
                    .background(Color.Black)
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(Uri.parse(item.url), if (item.isVideo) "video/*" else "image/*")
                        context.startActivity(intent)
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = item.thumbnailUrl.ifBlank { if (item.isVideo) null else item.url },
                    contentDescription = if (item.isVideo) "Vídeo da ocorrência" else "Fotografia da ocorrência",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                if (item.isVideo) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Reproduzir vídeo", tint = Color.White, modifier = Modifier.size(40.dp))
                }
            }
        }
    }
    if (pendingCount > 0) PendingMediaCard(Modifier.padding(top = 8.dp))
}

@Composable
fun PendingMediaCard(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CloudUpload, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Multimédia em envio (será carregada quando houver rede)…")
        }
    }
}

/** Pré-visualização dos anexos escolhidos no formulário, com opção de remover. */
@Composable
fun AttachmentPreviewRow(
    attachments: List<String>,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { uri ->
            Box(modifier = Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black)) {
                AsyncImage(
                    model = uri,
                    contentDescription = "Anexo selecionado",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(onClick = { onRemove(uri) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remover anexo", tint = Color.White)
                }
            }
        }
    }
}
