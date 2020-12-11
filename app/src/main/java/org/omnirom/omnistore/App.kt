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

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

import org.omnirom.omnistore.Constants.NOTIFICATION_CHANNEL_PROGRESS
import org.omnirom.omnistore.Constants.NOTIFICATION_CHANNEL_UPDATE


class App : Application() {
    private val TAG = "OmniStore:App"
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_PROGRESS) == null) {
            val channelProgress = NotificationChannel(
                NOTIFICATION_CHANNEL_PROGRESS,
                getString(R.string.notification_channel_downloads),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channelProgress)
        }
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_UPDATE) == null) {
            val channelUpdate = NotificationChannel(
                NOTIFICATION_CHANNEL_UPDATE,
                getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channelUpdate)
        }
    }
}