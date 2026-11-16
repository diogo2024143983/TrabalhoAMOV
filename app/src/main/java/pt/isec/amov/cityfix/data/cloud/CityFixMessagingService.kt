package pt.isec.amov.cityfix.data.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import pt.isec.amov.cityfix.MainActivity
import pt.isec.amov.cityfix.data.notifications.TicketNotifier

class CityFixMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        android.util.Log.i("CityFixFCM", "Mensagem recebida de ${message.from}: ${message.notification?.title}")
        val title = message.notification?.title ?: message.data["title"] ?: "CityFix"
        val body = message.notification?.body ?: message.data["body"] ?: "Há uma atualização numa ocorrência."
        // Dado opcional "ticketId" (enviado na consola em "Opções adicionais → Dados personalizados"):
        // ao tocar na notificação abre o ticket. Com a app em segundo plano o sistema entrega os
        // mesmos dados como extras do Intent, lidos pela MainActivity com a mesma chave.
        val ticketId = message.data[TicketNotifier.EXTRA_TICKET_ID]
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            ticketId?.let { putExtra(TicketNotifier.EXTRA_TICKET_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            ticketId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        NotificationManagerCompat.from(this).notify(message.messageId?.hashCode() ?: body.hashCode(), notification)
    }

    override fun onNewToken(token: String) {
        (applicationContext as pt.isec.amov.cityfix.data.CityFixApp).cloudRepository.saveMessagingToken(token)
    }

    companion object {
        const val CHANNEL_ID = "cityfix_updates"

        fun createChannel(manager: NotificationManager) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "CityFix updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Updates about reported incidents"
                    }
                )
            }
        }
    }
}
