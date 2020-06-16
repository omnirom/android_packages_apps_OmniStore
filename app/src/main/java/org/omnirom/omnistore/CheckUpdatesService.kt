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
import androidx.preference.PreferenceManager

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

    private fun createNotification(
        context: Context,
        title: String,
        message1: String,
        message2: String
    ): Notification? {
        val notification = NotificationCompat.Builder(
            context,
            Constants.NOTIFICATION_CHANNEL_UPDATE
        )
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setColor(context.resources.getColor(R.color.omni_logo_color))

        when {
            message2.isNullOrEmpty() and !message1.isNullOrEmpty() -> {
                notification.setContentText(message1)
            }
            message1.isNullOrEmpty() and !message2.isNullOrEmpty() -> {
                notification.setContentText(message2)
            }
            else -> {
                notification.setStyle(
                    NotificationCompat.InboxStyle()
                        .addLine(message1)
                        .addLine(message2)
                )
            }
        }
        val showApp = Intent(context, MainActivity::class.java)
        val showAppIntent: PendingIntent = PendingIntent.getActivity(
            context,
            showApp.hashCode(), showApp, PendingIntent.FLAG_UPDATE_CURRENT
        )
        notification.setContentIntent(showAppIntent)
        return notification.build()
    }

    private fun checkForUpdates(params: JobParameters?) {
        val newAppsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(this, Runnable { }, object :
                NetworkUtils.NetworkTaskCallback {
                override fun postAction(networkError: Boolean) {
                    if (!networkError) {
                        newAppsList.forEach { it.updateAppStatus(this@CheckUpdatesService.packageManager) }
                        val updateApps = newAppsList.filter { it.updateAvailable() }

                        val prefs =
                            PreferenceManager.getDefaultSharedPreferences(this@CheckUpdatesService)
                        val oldPkgList =
                            prefs.getStringSet(Constants.PREF_CURRENT_APPS, HashSet<String>())
                        val newAppsList = newAppsList.filter { !oldPkgList!!.contains(it.pkg()) }

                        var message1 = ""
                        var message2 = ""

                        if (updateApps.isNotEmpty()) {
                            Log.d(TAG, "checkForUpdates: updates available " + updateApps)
                            updateApps.forEach { message1 += it.title() + ", " }
                            message1 = getString(R.string.notification_updates_line_one) + " " + message1.substring(0, message1.length - 2)
                        }
                        if (newAppsList.isNotEmpty()) {
                            Log.d(TAG, "checkForUpdates: new apps available " + newAppsList)
                            newAppsList.forEach { message2 += it.title() + ", " }
                            message2 = getString(R.string.notification_updates_line_two) + " " + message2.substring(0, message2.length - 2)
                        }

                        val notification =
                            createNotification(
                                this@CheckUpdatesService,
                                getString(R.string.notification_updates_title),
                                message1,
                                message2
                            )

                        val notificationManager =
                            this@CheckUpdatesService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(NOTIFICATION_UPDATES_ID)
                        notificationManager.notify(NOTIFICATION_UPDATES_ID, notification)
                    }
                    jobFinished(params, false)
                }
            }, newAppsList)
        fetchApps.execute()
    }
}