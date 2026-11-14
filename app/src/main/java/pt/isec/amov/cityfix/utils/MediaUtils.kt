package pt.isec.amov.cityfix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import pt.isec.amov.cityfix.model.PreparedMedia
import java.io.File
import java.io.FileOutputStream

/**
 * Preparação local de multimédia antes do envio para a cloud:
 *  - imagens: correção de orientação EXIF, redimensionamento, compressão JPEG e miniatura;
 *  - vídeos: cópia para armazenamento interno e miniatura a partir do primeiro frame.
 * Os ficheiros resultantes ficam em filesDir/pending_uploads até o MediaUploadWorker os enviar.
 */
object MediaUtils {
    private const val MAX_IMAGE_DIMENSION = 1280
    private const val THUMBNAIL_DIMENSION = 256
    private const val IMAGE_QUALITY = 80
    private const val THUMBNAIL_QUALITY = 70
    private const val PROFILE_DIMENSION = 512

    /** Deve ser chamado fora da main thread (descodificação de imagens/vídeo). */
    fun prepare(context: Context, sourceUri: Uri, prefix: String): PreparedMedia? = runCatching {
        val dir = File(context.filesDir, "pending_uploads").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val mime = context.contentResolver.getType(sourceUri).orEmpty()

        if (mime.startsWith("video")) {
            val videoFile = File(dir, "${prefix}_$stamp.mp4")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(videoFile).use { input.copyTo(it) }
            } ?: return null
            val frame = MediaMetadataRetriever().run {
                try {
                    setDataSource(context, sourceUri)
                    getFrameAtTime(0)
                } finally {
                    release()
                }
            }
            val thumb = frame?.let {
                saveJpeg(scaleDown(it, THUMBNAIL_DIMENSION), File(dir, "${prefix}_${stamp}_thumb.jpg"), THUMBNAIL_QUALITY)
            }
            PreparedMedia(videoFile.absolutePath, thumb?.absolutePath, isVideo = true)
        } else {
            val bitmap = decodeSampled(context, sourceUri, MAX_IMAGE_DIMENSION) ?: return null
            val oriented = applyExifRotation(context, sourceUri, bitmap)
            val main = saveJpeg(scaleDown(oriented, MAX_IMAGE_DIMENSION), File(dir, "${prefix}_$stamp.jpg"), IMAGE_QUALITY)
            val thumb = saveJpeg(scaleDown(oriented, THUMBNAIL_DIMENSION), File(dir, "${prefix}_${stamp}_thumb.jpg"), THUMBNAIL_QUALITY)
            PreparedMedia(main.absolutePath, thumb.absolutePath, isVideo = false)
        }
    }.getOrNull()

    /** Ficheiro temporário exposto via FileProvider para o contrato TakePicture (padrão FileUtils de P09). */
    fun createTempImageFile(context: Context): Pair<File, Uri> {
        val imagesDir = File(context.filesDir, "images").apply { if (!exists()) mkdirs() }
        val tempFile = File(imagesDir, "CAPTURE_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        return tempFile to uri
    }

    /** Fotografia de perfil: redimensionada a 512 px e guardada como cópia local permanente. */
    fun compressProfilePhoto(context: Context, sourceUri: Uri): Uri? = runCatching {
        val bitmap = decodeSampled(context, sourceUri, PROFILE_DIMENSION) ?: return null
        val oriented = applyExifRotation(context, sourceUri, bitmap)
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = saveJpeg(scaleDown(oriented, PROFILE_DIMENSION), File(dir, "profile_${System.currentTimeMillis()}.jpg"), IMAGE_QUALITY)
        Uri.fromFile(file)
    }.getOrNull()

    fun deleteFileFromPath(path: String?): Boolean =
        !path.isNullOrBlank() && runCatching { File(path).let { it.exists() && it.delete() } }.getOrDefault(false)

    private fun decodeSampled(context: Context, uri: Uri, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= target) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val ratio = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height, 1f)
        if (ratio >= 1f) return bitmap
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun saveJpeg(bitmap: Bitmap, file: File, quality: Int): File {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        return file
    }
}
