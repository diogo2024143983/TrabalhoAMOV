package pt.isec.amov.cityfix.data.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import pt.isec.amov.cityfix.data.CityFixApp
import pt.isec.amov.cityfix.model.MediaAttachment
import pt.isec.amov.cityfix.model.PreparedMedia
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Envia para o Firebase Storage a multimédia (já comprimida localmente) de um ticket
 * e, no fim, associa-a ao documento do ticket no Firestore.
 * Só corre com ligação à rede; em caso de falha volta a tentar com backoff exponencial.
 */
class MediaUploadWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val ticketId = inputData.getString(KEY_TICKET_ID) ?: return Result.failure()
        val kind = inputData.getString(KEY_KIND) ?: KIND_MEDIA
        val mediaFile = inputData.getString(KEY_MEDIA_PATH)?.let(::File)
        val thumbFile = inputData.getString(KEY_THUMB_PATH)?.let(::File)?.takeIf { it.exists() }
        val isVideo = inputData.getBoolean(KEY_IS_VIDEO, false)

        if (mediaFile == null || !mediaFile.exists()) return Result.failure()
        val repository = (applicationContext as CityFixApp).cloudRepository
        if (!repository.isAvailable) return Result.failure()

        return try {
            val basePath = "tickets/$ticketId/${kind}_${mediaFile.nameWithoutExtension}"
            val mediaUrl = repository.uploadFileBlocking(mediaFile, "$basePath.${mediaFile.extension}")
            val thumbUrl: String = if (thumbFile != null) repository.uploadFileBlocking(thumbFile, "${basePath}_thumb.jpg") else ""
            val attachment = MediaAttachment(mediaUrl, thumbUrl, isVideo)

            if (kind == KIND_PROOF) {
                repository.attachResolutionProofBlocking(ticketId, attachment)
            } else {
                repository.attachMediaBlocking(ticketId, attachment)
            }
            mediaFile.delete()
            thumbFile?.delete()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KIND_MEDIA = "media"
        const val KIND_PROOF = "proof"

        private const val KEY_TICKET_ID = "ticketId"
        private const val KEY_KIND = "kind"
        private const val KEY_MEDIA_PATH = "mediaPath"
        private const val KEY_THUMB_PATH = "thumbPath"
        private const val KEY_IS_VIDEO = "isVideo"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context, ticketId: String, kind: String, index: Int, media: PreparedMedia) {
            val request = OneTimeWorkRequestBuilder<MediaUploadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setInputData(
                    workDataOf(
                        KEY_TICKET_ID to ticketId,
                        KEY_KIND to kind,
                        KEY_MEDIA_PATH to media.mediaPath,
                        KEY_THUMB_PATH to media.thumbnailPath,
                        KEY_IS_VIDEO to media.isVideo
                    )
                )
                .addTag("upload_$ticketId")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork("upload_${kind}_${ticketId}_$index", ExistingWorkPolicy.REPLACE, request)
        }
    }
}
