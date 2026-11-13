package pt.isec.amov.cityfix.data.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pt.isec.amov.cityfix.MainActivity
import pt.isec.amov.cityfix.data.cloud.CityFixMessagingService
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.utils.CityFixUtils

/**
 * Gera notificações locais a partir da comparação entre o último snapshot conhecido dos tickets
 * (guardado localmente) e o snapshot atual do Firestore. É usado tanto pelo listener em tempo real
 * (app aberta) como pelo TicketLifecycleWorker (app em segundo plano), dispensando Cloud Functions.
 */
class TicketNotifier(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun notifyChanges(previous: List<Incident>, current: List<Incident>, myUid: String?, myRole: UserRole) {
        if (myUid.isNullOrBlank() || myRole == UserRole.ANONYMOUS) return
        if (previous.isEmpty()) return // primeiro snapshot: sem base de comparação

        val previousById = previous.associateBy { it.id }
        current.forEach { ticket ->
            if (!isRelevant(ticket, myUid, myRole)) return@forEach
            val old = previousById[ticket.id]

            if (old == null) {
                if (myRole == UserRole.OPERATOR && ticket.authorId != myUid) {
                    show(ticket.id, 0, "Nova ocorrência", "${ticket.categoryName}: ${ticket.title}")
                }
                return@forEach
            }

            if (old.status != ticket.status && ticket.history.lastOrNull()?.byUid != myUid) {
                val text = when (ticket.status) {
                    IncidentStatus.OPEN -> if (old.status == IncidentStatus.RESOLVED) "A ocorrência foi reaberta." else "A ocorrência voltou a estar aberta."
                    IncidentStatus.INFORMATION_REQUEST -> "Foi pedido um esclarecimento. Responda nas mensagens do ticket."
                    IncidentStatus.IN_RESOLUTION -> "A equipa municipal iniciou a resolução."
                    IncidentStatus.RESOLVED -> "O problema foi resolvido. Pode reabrir durante 5 dias."
                    IncidentStatus.CLOSED -> "O ticket foi encerrado e arquivado."
                }
                show(ticket.id, 1, "\"${ticket.title}\" – ${statusName(ticket.status)}", text)
            }

            if (ticket.lastMessageAt > old.lastMessageAt && ticket.lastMessageAuthorId != myUid) {
                show(ticket.id, 2, "Nova mensagem: ${ticket.title}", ticket.lastMessageText)
            }
        }
    }

    /** Alerta o munícipe quando faltam 24 horas para o fecho definitivo de um ticket resolvido. */
    fun notifyClosingSoon(current: List<Incident>, myUid: String?, now: Long = System.currentTimeMillis()) {
        if (myUid.isNullOrBlank()) return
        val alreadyNotified = prefs.getStringSet(KEY_CLOSING_NOTIFIED, emptySet()).orEmpty().toMutableSet()
        var changed = false

        current.filter { it.status == IncidentStatus.RESOLVED && (it.authorId == myUid || myUid in it.supporterIds) }
            .forEach { ticket ->
                val resolvedAt = ticket.resolvedAt ?: return@forEach
                val remaining = resolvedAt + CityFixUtils.REOPEN_WINDOW_MS - now
                val key = "${ticket.id}_$resolvedAt"
                if (remaining in 1..DAY_MS && key !in alreadyNotified) {
                    show(
                        ticket.id, 3,
                        "Último dia para reabrir",
                        "\"${ticket.title}\" será encerrado definitivamente dentro de 24 horas."
                    )
                    alreadyNotified += key
                    changed = true
                }
            }
        if (changed) prefs.edit().putStringSet(KEY_CLOSING_NOTIFIED, alreadyNotified).apply()
    }

    private fun isRelevant(ticket: Incident, myUid: String, myRole: UserRole): Boolean =
        myRole == UserRole.OPERATOR || ticket.authorId == myUid || myUid in ticket.supporterIds

    private fun show(ticketId: String, kind: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TICKET_ID, ticketId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, ticketId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CityFixMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(context).notify(ticketId.hashCode() * 31 + kind, notification)
    }

    private fun statusName(status: IncidentStatus) = when (status) {
        IncidentStatus.OPEN -> "Aberto"
        IncidentStatus.INFORMATION_REQUEST -> "Pedido de Informações"
        IncidentStatus.IN_RESOLUTION -> "Em Resolução"
        IncidentStatus.RESOLVED -> "Resolvido"
        IncidentStatus.CLOSED -> "Encerrado"
    }

    companion object {
        const val EXTRA_TICKET_ID = "ticketId"
        private const val PREFS = "cityfix_notifications"
        private const val KEY_CLOSING_NOTIFIED = "closing_notified"
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
