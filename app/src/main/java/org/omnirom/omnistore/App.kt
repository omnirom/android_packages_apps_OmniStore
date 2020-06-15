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