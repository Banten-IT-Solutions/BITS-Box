package id.bits.box.bg

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.PowerManager
import id.bits.box.BitsBoxApp

class ProxyService : Service(), BaseService.Interface {
    override val data = BaseService.Data(this)
    override val tag: String get() = "BITSBoxProxyService"
    override fun createNotification(profileName: String): ServiceNotification =
        ServiceNotification(this, profileName, "service-proxy", true)

    override var wakeLock: PowerManager.WakeLock? = null
    override var upstreamInterfaceName: String? = null

    @SuppressLint("WakelockTimeout")
    override fun acquireWakeLock() {
        wakeLock = BitsBoxApp.power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bitsbox:proxy")
            .apply { acquire() }
    }

    override fun onBind(intent: Intent) = super.onBind(intent)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        super<BaseService.Interface>.onStartCommand(intent, flags, startId)
}
