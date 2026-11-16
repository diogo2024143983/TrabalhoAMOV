package pt.isec.amov.cityfix.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import pt.isec.amov.cityfix.data.CityFixApp
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.Incident

/**
 * Geofence de 20 m do operador municipal (padrão ProximityService de P10).
 * Serviço em primeiro plano que recebe atualizações de localização e, quando o operador entra no
 * raio de uma ocorrência ativa, regista-o no ticket: os autores são notificados pelo TicketNotifier.
 */
class ProximityService : Service() {
    private val app by lazy { applicationContext as CityFixApp }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    /** Tickets em que o operador já foi assinalado; só volta a notificar depois de sair do raio. */
    private val notifiedTickets = mutableSetOf<String>()
    private var activeIncidents: List<Incident> = emptyList()
    private var ticketsListener: ListenerRegistration? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var operatorName = "Operador municipal"

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { checkProximity(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground()
        // O utilizador vem do DataStore (leitura suspensa) e fica em cache enquanto o serviço corre
        serviceScope.launch {
            app.localRepository.user.collect { user ->
                operatorName = user?.name.orEmpty().ifBlank { "Operador municipal" }
            }
        }
        ticketsListener = app.cloudRepository.observeTickets(onChanged = { tickets ->
            activeIncidents = tickets.filter { it.isActive }
        })
        startLocationUpdates()
    }

    private fun promoteToForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("CityFix – Operador em serviço")
            .setContentText("A detetar ocorrências ativas num raio de 20 m.")
            .setOngoing(true)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, FOREGROUND_ID, notification, type)
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15000)
            .setMinUpdateIntervalMillis(10000)
            .build()
        try {
            locationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            Log.w(TAG, "Sem permissão de localização", e)
            stopSelf()
        }
    }

    private fun checkProximity(userLoc: Location) {
        val results = FloatArray(1)
        activeIncidents.forEach { ticket ->
            if (ticket.latitude == 0.0 && ticket.longitude == 0.0) return@forEach
            Location.distanceBetween(userLoc.latitude, userLoc.longitude, ticket.latitude, ticket.longitude, results)
            val distance = results[0]
            Log.i(TAG, "Distância à ocorrência ${ticket.title}: $distance m")
            if (distance <= BusinessRules.OPERATOR_PROXIMITY_RADIUS_METERS) {
                if (notifiedTickets.add(ticket.id)) {
                    app.cloudRepository.markOperatorNearby(ticket.id, operatorName)
                    app.ticketNotifier.notifyOperatorNearIncident(ticket, distance)
                }
            } else {
                notifiedTickets.remove(ticket.id)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Serviço de proximidade", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationClient.removeLocationUpdates(locationCallback)
        ticketsListener?.remove()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AMOV"
        private const val CHANNEL_ID = "proximity_channel"
        private const val FOREGROUND_ID = 2001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ProximityService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ProximityService::class.java))
        }
    }
}
