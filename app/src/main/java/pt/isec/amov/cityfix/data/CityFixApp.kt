package pt.isec.amov.cityfix.data

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import org.osmdroid.config.Configuration
import pt.isec.amov.cityfix.data.cloud.CityFixFirebaseRepository
import pt.isec.amov.cityfix.data.cloud.CityFixMessagingService
import pt.isec.amov.cityfix.data.local.CityFixLocalRepository
import pt.isec.amov.cityfix.data.notifications.TicketNotifier
import pt.isec.amov.cityfix.data.work.TicketLifecycleWorker

/**
 * Objeto Application: ponto único de criação dos repositórios (padrão do ContactsApp das aulas).
 * Os ViewModels recebem-nos através das respetivas ViewModelProvider.Factory.
 */
class CityFixApp : Application() {
    val cloudRepository by lazy { CityFixFirebaseRepository(this) }
    val localRepository by lazy { CityFixLocalRepository(this) }
    val mediaRepository by lazy { MediaRepository(this) }
    val ticketNotifier by lazy { TicketNotifier(this) }

    override fun onCreate() {
        super.onCreate()
        // osmdroid exige um user agent identificado para descarregar os mosaicos do OpenStreetMap
        Configuration.getInstance().userAgentValue = packageName
        CityFixMessagingService.createChannel(
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        )
        TicketLifecycleWorker.schedule(this)
        TicketLifecycleWorker.runNow(this)
    }
}
