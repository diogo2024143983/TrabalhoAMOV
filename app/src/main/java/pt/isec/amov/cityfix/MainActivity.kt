package pt.isec.amov.cityfix

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import pt.isec.amov.cityfix.data.CityFixApp
import pt.isec.amov.cityfix.data.notifications.TicketNotifier
import pt.isec.amov.cityfix.ui.screens.MainScreen
import pt.isec.amov.cityfix.ui.theme.CityFixTheme

class MainActivity : ComponentActivity() {
    /** Ticket a abrir quando a app é iniciada a partir de uma notificação. */
    private val pendingTicketId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTicketId.value = intent?.getStringExtra(TicketNotifier.EXTRA_TICKET_ID)
        setContent {
            CityFixTheme {
                MainScreen(
                    app = application as CityFixApp,
                    pendingTicketId = pendingTicketId.value,
                    onPendingTicketHandled = { pendingTicketId.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(TicketNotifier.EXTRA_TICKET_ID)?.let { pendingTicketId.value = it }
    }
}