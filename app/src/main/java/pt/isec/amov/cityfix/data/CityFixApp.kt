package pt.isec.amov.cityfix.data

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import pt.isec.amov.cityfix.data.cloud.CityFixMessagingService
import pt.isec.amov.cityfix.data.work.TicketLifecycleWorker

class CityFixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CityFixMessagingService.createChannel(
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        )
        TicketLifecycleWorker.schedule(this)
        TicketLifecycleWorker.runNow(this)
    }
}
