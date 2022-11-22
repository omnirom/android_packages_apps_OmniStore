/*
 *  Copyright (C) 2022 The OmniROM Project
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
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import androidx.work.*

class CheckUpdatesWorker(val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    private val TAG = "OmniStore:CheckUpdatesWorker"
    private val NOTIFICATION_UPDATES_ID = Int.MAX_VALUE - 1;

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork")
        checkForUpdates()
        return Result.success()
    }

    class Factory() : WorkerFactory() {
        override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
            return CheckUpdatesWorker(appContext, workerParameters)
        }
    }

    private fun createNotification(
        title: String,
        message1: String,
        message2: String
    ): Notification {
        val notification = NotificationCompat.Builder(
            context,
            Constants.NOTIFICATION_CHANNEL_UPDATE
        )
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setColor(context.resources.getColor(R.color.omni_logo_color, null))

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
        val showApp = Intent(context, MainActivity::class.java)
        val showAppIntent: PendingIntent = PendingIntent.getActivity(
            context,
            showApp.hashCode(),
            showApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notification.setContentIntent(showAppIntent)
        return notification.build()
    }

    private fun checkForUpdates() {
        val appsList = mutableListOf<AppItem>()
        val fetchApps =
            NetworkUtils.FetchAppsTask(context, Runnable { }, object :
                NetworkUtils.NetworkTaskCallback {
                override fun postAction(networkError: Boolean, reponseCode: Int) {
                    if (!networkError) {
                        appsList.forEach { it.updateAppStatus(context.packageManager) }
                        val updateApps = appsList.filter { it.updateAvailable() }

                        val prefs =
                            PreferenceManager.getDefaultSharedPreferences(context)
                        val oldAllPkgList =
                            prefs.getStringSet(Constants.PREF_CURRENT_APPS, HashSet<String>())
                        val oldUpdatePkgList =
                            prefs.getStringSet(Constants.PREF_UPDATE_APPS, HashSet<String>())

                        val newAllAppsList = appsList.filter { !oldAllPkgList!!.contains(it.packageName) }
                        val newUpdateAppsList = appsList.filter { it.updateAvailable() }.filter { !oldUpdatePkgList!!.contains(it.packageName) }

                        val notificationManager =
                            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                        if (newUpdateAppsList.isNotEmpty() || newAllAppsList.isNotEmpty()) {
                            var message1 = ""
                            var message2 = ""

                            if (updateApps.isNotEmpty()) {
                                Log.d(TAG, "checkForUpdates: updates available " + updateApps)
                                updateApps.slice(0..(updateApps.size - 1).coerceAtMost(2))
                                    .forEach { message1 += it.title() + ", " }
                                message1 =
                                    context.resources.getString(R.string.notification_updates_line_one) + " " + message1.substring(
                                        0,
                                        message1.length - 2
                                    )
                                if (updateApps.size > 3) {
                                    message1 += ",..."
                                }
                            }
                            if (newAllAppsList.isNotEmpty()) {
                                Log.d(TAG, "checkForUpdates: new apps available " + newAllAppsList)
                                newAllAppsList.slice(0..(newAllAppsList.size - 1).coerceAtMost(2))
                                    .forEach { message2 += it.title() + ", " }
                                message2 =
                                    context.resources.getString(R.string.notification_updates_line_two) + " " + message2.substring(
                                        0,
                                        message2.length - 2
                                    )
                                if (newAllAppsList.size > 3) {
                                    message2 += ",..."
                                }
                            }

                            if (message1.isNotEmpty() or message2.isNotEmpty()) {
                                val notification =
                                    createNotification(
                                        context.resources.getString(R.string.notification_updates_title),
                                        message1,
                                        message2
                                    )

                                notificationManager.cancel(NOTIFICATION_UPDATES_ID)
                                notificationManager.notify(NOTIFICATION_UPDATES_ID, notification)
                            }
                        } else {
                            notificationManager.cancel(NOTIFICATION_UPDATES_ID)
                        }
                    }
                }
            }, appsList)
        fetchApps.run()
    }
}