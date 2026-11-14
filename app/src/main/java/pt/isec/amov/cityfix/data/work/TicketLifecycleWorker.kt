package pt.isec.amov.cityfix.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import pt.isec.amov.cityfix.data.CityFixApp
import pt.isec.amov.cityfix.model.BusinessRules
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import java.util.concurrent.TimeUnit

/**
 * Tarefa periódica em segundo plano:
 *  1. encerra (arquiva) os tickets resolvidos há mais de 5 dias;
 *  2. gera notificações locais de mudanças de estado / novas mensagens ocorridas com a app fechada;
 *  3. avisa o munícipe quando faltam 24 horas para o fecho definitivo.
 */
class TicketLifecycleWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val app = applicationContext as CityFixApp
        val cloud = app.cloudRepository
        if (!cloud.isAvailable) return Result.success()
        val uid = cloud.currentUserId ?: return Result.success()

        val local = app.localRepository
        val role = local.loadUser()?.role ?: UserRole.ANONYMOUS
        val notifier = app.ticketNotifier
        val now = System.currentTimeMillis()

        var tickets = cloud.fetchTicketsBlocking()
        if (tickets.isEmpty()) return Result.success()

        val expired = tickets.filter {
            it.status == IncidentStatus.RESOLVED && it.resolvedAt != null &&
                now - it.resolvedAt >= BusinessRules.REOPEN_WINDOW_MS
        }
        if (expired.isNotEmpty()) {
            expired.forEach { cloud.closeIfExpiredBlocking(it.id, now) }
            tickets = cloud.fetchTicketsBlocking()
        }

        notifier.notifyChanges(local.loadIncidents(emptyList()), tickets, uid, role)
        notifier.notifyClosingSoon(tickets, uid, now)
        local.saveIncidents(tickets)
        return Result.success()
    }

    companion object {
        private const val PERIODIC_NAME = "ticket_lifecycle_periodic"
        private const val ONE_SHOT_NAME = "ticket_lifecycle_now"

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Agenda a verificação periódica (mínimo permitido pelo WorkManager: 15 minutos). */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TicketLifecycleWorker>(15, TimeUnit.MINUTES)
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Corre uma verificação imediata (ex.: ao abrir a app ou após login). */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<TicketLifecycleWorker>()
                .setConstraints(networkConstraint)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_SHOT_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
