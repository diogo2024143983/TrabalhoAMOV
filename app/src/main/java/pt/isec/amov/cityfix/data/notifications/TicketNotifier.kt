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
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.model.Incident
import pt.isec.amov.cityfix.model.IncidentStatus
import pt.isec.amov.cityfix.model.UserRole
import pt.isec.amov.cityfix.model.BusinessRules

/**
 * Gera notificações locais a partir da comparação entre o último snapshot conhecido dos tickets
 * (guardado localmente) e o snapshot atual do Firestore. É usado tanto pelo listener em tempo real
 * (app aberta) como pelo TicketLifecycleWorker (app em segundo plano), dispensando Cloud Functions.
 */
class TicketNotifier(
    private val context: Context,
    private val localRepository: CityFixLocalRepository
) {

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

            // Geofence de 20 m: o operador aproximou-se de uma ocorrência que o municípe acompanha
            if (myRole != UserRole.OPERATOR && ticket.operatorNearbyAt > old.operatorNearbyAt) {
                show(
                    ticket.id, 4,
                    "A equipa camarária está na área",
                    "${ticket.operatorNearbyName.ifBlank { "Um operador" }} está junto de \"${ticket.title}\"."
                )
            }
        }
    }

    /** Notificação para o próprio operador quando entra no raio de 20 m de uma ocorrência ativa. */
    fun notifyOperatorNearIncident(ticket: Incident, distanceMeters: Float) {
        show(
            ticket.id, 5,
            "Ocorrência ativa nas proximidades",
            "Está a %.0f m de \"%s\". Os autores foram avisados.".format(distanceMeters, ticket.title)
        )
    }

    /** Notificação com o código de 2.º fator (MFA) aquando do login (enunciado I.2.B / I.3.B). */
    fun notifyMfaCode(code: String) {
        show(
            ticketId = "mfa",
            kind = 6,
            title = "Código de Segurança CityFix",
            text = "O teu código de confirmação é $code. Introduz este código na aplicação para entrar."
        )
    }

    /** Alerta o munícipe quando faltam 24 horas para o fecho definitivo de um ticket resolvido. */
    suspend fun notifyClosingSoon(current: List<Incident>, myUid: String?, now: Long = System.currentTimeMillis()) {
        if (myUid.isNullOrBlank()) return
        val alreadyNotified = localRepository.loadClosingNotified().toMutableSet()
        var changed = false

        current.filter { it.status == IncidentStatus.RESOLVED && (it.authorId == myUid || myUid in it.supporterIds) }
            .forEach { ticket ->
                val resolvedAt = ticket.resolvedAt ?: return@forEach
                val remaining = resolvedAt + BusinessRules.REOPEN_WINDOW_MS - now
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
        if (changed) localRepository.saveClosingNotified(alreadyNotified)
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
        // Agrupamento por ticket com resumo próprio: evita o agrupamento automático do sistema,
        // cujo toque abriria a app sem indicar qual o ticket.
        val group = "ticket_$ticketId"
        val notification = NotificationCompat.Builder(context, CityFixMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(group)
            .build()
        val summary = NotificationCompat.Builder(context, CityFixMessagingService.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setGroup(group)
            .setGroupSummary(true)
            .build()
        val manager = NotificationManagerCompat.from(context)
        manager.notify(ticketId.hashCode() * 31 + kind, notification)
        manager.notify(ticketId.hashCode() * 31 + SUMMARY_ID, summary)
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


        private const val DAY_MS = 17L * 60 * 1000L //para testar----avisa quando faltar 17 min
        //private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val SUMMARY_ID = 9
    }
}
