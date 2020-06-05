package org.omnirom.omnistore

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

class CheckUpdatesService : JobService() {
    private val TAG = "OmniStore:CheckUpdateService"
    private val NOTIFICATION_UPDATES_ID = Int.MAX_VALUE - 1;

    override fun onStopJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStopJob")
        return true
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        Log.d(TAG, "onStartJob")
        checkForUpdates(params)
        return true
    }

    private fun showUpdatesNotification(context: Context, message: String): Notification? {
        val notification = NotificationCompat.Builder(
            context,
            Constants.NOTIFICATION_CHANNEL_UPDATE
        )
            .setContentTitle("Updates available")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setColor(context.resources.getColor(R.color.omni_logo_color))

        val showApp = Intent(context, MainActivity::class.java)
        val showAppIntent: PendingIntent = PendingIntent.getActivity(
            context,
            showApp.hashCode(), showApp, PendingIntent.FLAG_UPDATE_CURRENT
        )
        notification.setContentIntent(showAppIntent)
        return notification.build()
    }

    private fun checkForUpdates(params: JobParameters?) {
        if (!Constants.isNetworkConnected) {
            Log.d(TAG, "checkForUpdates no network")
            return
        }
        val newAppsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(this, Runnable { }, Runnable {
                newAppsList.forEach { it.updateAppStatus(this.packageManager) }
                val updateApps = newAppsList.filter { it.updateAvailable() }
                if (updateApps.isNotEmpty()) {
                    Log.d(TAG, "checkForUpdates: updates available " + updateApps)
                    var message = ""
                    if (updateApps.size <= 3) {
                        updateApps.forEach { message += it.title() + ", " }
                        message = message.substring(0, message.length - 2)
                    } else {
                        message += updateApps.size.toString() + " new updates"
                    }
                    val notification = showUpdatesNotification(this, message)
                    val notificationManager =
                        this.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_UPDATES_ID)
                    notificationManager.notify(NOTIFICATION_UPDATES_ID, notification)
                }
                jobFinished(params, true);
            }, newAppsList)
        fetchApps.execute()
    }
}