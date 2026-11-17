package pt.isec.amov.cityfix.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

/** Localização atual do dispositivo e conversão de moradas (Geocoder). */
object LocationTracker {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun currentLocation(context: Context): Location? {
        if (!hasPermission(context)) return null
        val client = LocationServices.getFusedLocationProviderClient(context)
        return suspendCancellableCoroutine { cont ->
            try {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            } catch (_: SecurityException) {
                cont.resume(null)
            }
        }
    }

    /** Converte uma morada escrita (ex: "Rua de Angola 25") em coordenadas (latitude, longitude). */
    suspend fun geocodeAddress(context: Context, address: String): Pair<Double, Double>? {
        val clean = address.trim()
        if (clean.isBlank()) return null
        // Se o utilizador não escreveu a cidade, acrescenta Coimbra para não ir parar a outro país
        val query = if (clean.contains("coimbra", ignoreCase = true) || clean.contains("portugal", ignoreCase = true)) {
            clean
        } else {
            "$clean, Coimbra, Portugal"
        }
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocationName(query, 1) { addresses ->
                        val first = addresses.firstOrNull()
                        cont.resume(first?.let { it.latitude to it.longitude })
                    }
                } catch (_: Exception) {
                    cont.resume(null)
                }
            }
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query, 1)?.firstOrNull()?.let { it.latitude to it.longitude }
                }.getOrNull()
            }
        }
    }
}