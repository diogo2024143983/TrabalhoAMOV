package pt.isec.amov.cityfix.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.isec.amov.cityfix.data.work.MediaUploadWorker
import pt.isec.amov.cityfix.model.PreparedMedia
import pt.isec.amov.cityfix.utils.MediaUtils

/**
 * Acesso à multimédia local: compressão/miniaturas (Dispatchers.IO) e agendamento do envio
 * em segundo plano. Os ViewModels usam este repositório em vez de dependerem de um Context.
 */
class MediaRepository(private val context: Context) {

    suspend fun prepare(uri: Uri, prefix: String): PreparedMedia? = withContext(Dispatchers.IO) {
        MediaUtils.prepare(context, uri, prefix)
    }

    suspend fun compressProfilePhoto(uri: Uri): Uri? = withContext(Dispatchers.IO) {
        MediaUtils.compressProfilePhoto(context, uri)
    }

    /** Uri temporário para captura com a câmara (TakePicture). */
    fun createCaptureUri(): Uri = MediaUtils.createTempImageFile(context).second

    fun enqueueTicketMedia(ticketId: String, index: Int, media: PreparedMedia) =
        MediaUploadWorker.enqueue(context, ticketId, MediaUploadWorker.KIND_MEDIA, index, media)

    fun enqueueResolutionProof(ticketId: String, media: PreparedMedia) =
        MediaUploadWorker.enqueue(context, ticketId, MediaUploadWorker.KIND_PROOF, 0, media)
}
