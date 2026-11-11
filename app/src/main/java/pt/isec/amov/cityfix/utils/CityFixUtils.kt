package pt.isec.amov.cityfix.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentStatus
import java.io.File
import java.io.FileOutputStream
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object CityFixUtils {


    const val PRACA_REPUBLICA_LAT = 40.2095
    const val PRACA_REPUBLICA_LON = -8.4197

    const val ANONYMOUS_MAX_RADIUS_METERS = 10_000.0
    const val DUPLICATE_GROUP_RADIUS_METERS = 10.0
    const val OPERATOR_PROXIMITY_RADIUS_METERS = 20.0

    const val REOPEN_WINDOW_MS = 5L * 24 * 60 * 60 * 1000L

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun isWithinPracaRepublica(lat: Double, lon: Double): Boolean {
        return distanceMeters(PRACA_REPUBLICA_LAT, PRACA_REPUBLICA_LON, lat, lon) <= ANONYMOUS_MAX_RADIUS_METERS
    }

    //resolvido á 5 dias
    fun canReopenIncident(incident: Incident): Boolean {
        if (incident.status != IncidentStatus.RESOLVED) return false
        val resolvedAt = incident.resolvedAt ?: return false
        return (System.currentTimeMillis() - resolvedAt) <= REOPEN_WINDOW_MS
    }

    //valid do nif
    fun isValidPortugueseNif(nif: String): Boolean {
        val clean = nif.trim()
        if (!clean.matches(Regex("^[1235689][0-9]{8}$"))) return false
        var sum = 0
        for (i in 0 until 8) {
            sum += clean[i].digitToInt() * (9 - i)
        }
        val remainder = sum % 11
        val checkDigit = if (remainder == 0 || remainder == 1) 0 else 11 - remainder
        return checkDigit == clean[8].digitToInt()
    }

    fun isValidPhone(phone: String): Boolean {
        val clean = phone.replace(Regex("[\\s-]"), "")
        return clean.matches(Regex("^(9[1236][0-9]{7}|2[0-9]{8})$"))
    }

    fun isValidMunicipalEmail(email: String): Boolean {
        val clean = email.trim().lowercase()
        return clean.endsWith("@cm-coimbra.pt") ||
                clean.endsWith("@municipio.pt") ||
                clean.endsWith("@isec.pt")
    }

    fun calculateAverageRepairTimeDays(incidents: List<Incident>): Double {
        val resolvedList = incidents.filter { it.resolvedAt != null && it.resolvedAt > it.createdAt }
        if (resolvedList.isEmpty()) return 0.0
        val totalMs = resolvedList.sumOf { (it.resolvedAt ?: 0L) - it.createdAt }
        val avgMs = totalMs.toDouble() / resolvedList.size
        return avgMs / (1000.0 * 60 * 60 * 24)
    }
    fun compressAndSaveImage(context: Context, sourceUri: Uri, prefix: String = "img"): Uri? {
        return runCatching {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            if (originalBitmap == null) return null
            val maxDimension = 512
            val ratio = minOf(
                maxDimension.toFloat() / originalBitmap.width,
                maxDimension.toFloat() / originalBitmap.height,
                1f
            )
            val width = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
            val height = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true)
            val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
            Uri.fromFile(file)
        }.getOrNull()
    }

}