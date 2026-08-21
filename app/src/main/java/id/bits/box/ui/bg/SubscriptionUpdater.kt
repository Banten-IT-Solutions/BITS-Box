package id.bits.box.bg

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import id.bits.box.R
import id.bits.box.database.DataStore
import id.bits.box.database.BitsBoxDatabase
import id.bits.box.group.GroupUpdater
import id.bits.box.ktx.Logs
import id.bits.box.ktx.app
import java.util.concurrent.TimeUnit

object SubscriptionUpdater {

    private const val WORK_NAME = "SubscriptionUpdater"

    suspend fun reconfigureUpdater() {
        RemoteWorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)

        val subscriptions = BitsBoxDatabase.groupDao.subscriptions()
            .filter { it.subscription?.autoUpdate == true }
        if (subscriptions.isEmpty()) return

        var minDelay = subscriptions.minOfOrNull { it.subscription!!.autoUpdateDelay }?.toLong() ?: 15L
        val now = System.currentTimeMillis() / 1000L
        var minInitDelay = subscriptions.minOfOrNull { now - it.subscription!!.lastUpdated - (minDelay * 60) } ?: 60L
        if (minDelay < 15) minDelay = 15
        if (minInitDelay > 60) minInitDelay = 60

        // main process
        RemoteWorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WORK_NAME,
            UPDATE,
            PeriodicWorkRequest.Builder(UpdateTask::class.java, minDelay, TimeUnit.MINUTES)
                .apply {
                    if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                }
                .build()
        )
    }

    class UpdateTask(
        appContext: Context, params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        val nm = NotificationManagerCompat.from(applicationContext)

        fun canPostNotifications(): Boolean =
            Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

        val notification = NotificationCompat.Builder(applicationContext, "service-subscription")
            .setWhen(0)
            .setTicker(applicationContext.getString(R.string.forward_success))
            .setContentTitle(applicationContext.getString(R.string.subscription_update))
            .setSmallIcon(R.drawable.ic_service_active)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        override suspend fun doWork(): Result {
            val subscriptions = BitsBoxDatabase.groupDao.subscriptions()
                .filter { it.subscription?.autoUpdate == true }
            
            if (subscriptions.isEmpty()) return Result.success()

            for (profile in subscriptions) {
                val subscription = profile.subscription ?: continue

                val elapsed = (System.currentTimeMillis() / 1000).toInt() - subscription.lastUpdated
                val updateNow = !DataStore.serviceState.connected || elapsed >= subscription.autoUpdateDelay * 60
                
                if (!updateNow && DataStore.serviceState.connected) {
                    Logs.d("work: not updating ${profile.displayName()} (elapsed=$elapsed, required=${subscription.autoUpdateDelay}min)")
                    continue
                }
                Logs.d("work: updating ${profile.displayName()}")

                notification.setContentText(
                    applicationContext.getString(
                        R.string.subscription_update_message, profile.displayName()
                    )
                )
                if (canPostNotifications()) {
                    // Permission verified by canPostNotifications() above
                    @SuppressLint("MissingPermission")
                    nm.notify(2, notification.build())
                }

                GroupUpdater.executeUpdate(profile, false)
            }

            nm.cancel(2)
            return Result.success()
        }
    }

}