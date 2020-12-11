/*
 *  Copyright (C) 2020 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
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
        title: String,
        message1: String,
        message2: String
    ): Notification? {
        val notification = NotificationCompat.Builder(
            this,
            Constants.NOTIFICATION_CHANNEL_UPDATE
        )
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setColor(resources.getColor(R.color.omni_logo_color, null))

        when {
            message2.isEmpty() and message1.isNotEmpty() -> {
                notification.setContentText(message1)
            }
            message1.isEmpty() and message2.isNotEmpty() -> {
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
        val showApp = Intent(this, MainActivity::class.java)
        val showAppIntent: PendingIntent = PendingIntent.getActivity(
            this,
            showApp.hashCode(), showApp, PendingIntent.FLAG_UPDATE_CURRENT
        )
        notification.setContentIntent(showAppIntent)
        return notification.build()
    }

    private fun checkForUpdates(params: JobParameters?) {
        val appsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(this, Runnable { }, object :
                NetworkUtils.NetworkTaskCallback {
                override fun postAction(networkError: Boolean) {
                    if (!networkError) {
                        appsList.forEach { it.updateAppStatus(this@CheckUpdatesService.packageManager) }
                        val updateApps = appsList.filter { it.updateAvailable() }

                        val prefs =
                            PreferenceManager.getDefaultSharedPreferences(this@CheckUpdatesService)
                        val oldPkgList =
                            prefs.getStringSet(Constants.PREF_CURRENT_APPS, HashSet<String>())
                        val newAppsList = appsList.filter { !oldPkgList!!.contains(it.pkg()) }

                        if (updateApps.isNotEmpty() || newAppsList.isNotEmpty()) {
                            var message1 = ""
                            var message2 = ""

                            if (updateApps.isNotEmpty()) {
                                Log.d(TAG, "checkForUpdates: updates available " + updateApps)
                                updateApps.slice(0..(updateApps.size - 1).coerceAtMost(2))
                                    .forEach { message1 += it.title() + ", " }
                                message1 =
                                    getString(R.string.notification_updates_line_one) + " " + message1.substring(
                                        0,
                                        message1.length - 2
                                    )
                                if (updateApps.size > 3) {
                                    message1 += ",..."
                                }
                            }
                            if (newAppsList.isNotEmpty()) {
                                Log.d(TAG, "checkForUpdates: new apps available " + newAppsList)
                                newAppsList.slice(0..(newAppsList.size - 1).coerceAtMost(2))
                                    .forEach { message2 += it.title() + ", " }
                                message2 =
                                    getString(R.string.notification_updates_line_two) + " " + message2.substring(
                                        0,
                                        message2.length - 2
                                    )
                                if (newAppsList.size > 3) {
                                    message2 += ",..."
                                }
                            }

                            if (message1.isNotEmpty() or message2.isNotEmpty()) {
                                val notification =
                                    createNotification(
                                        getString(R.string.notification_updates_title),
                                        message1,
                                        message2
                                    )

                                val notificationManager =
                                    this@CheckUpdatesService.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                notificationManager.cancel(NOTIFICATION_UPDATES_ID)
                                notificationManager.notify(NOTIFICATION_UPDATES_ID, notification)
                            }
                        }
                    }
                    jobFinished(params, false)
                }
            }, appsList)
        fetchApps.execute()
    }
}