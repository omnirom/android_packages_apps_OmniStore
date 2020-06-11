package org.omnirom.omnistore

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
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

        val channelProgress = NotificationChannel(
            NOTIFICATION_CHANNEL_PROGRESS,
            "Download",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channelProgress)
        val channelUpdate = NotificationChannel(
            NOTIFICATION_CHANNEL_UPDATE,
            "Updates",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channelUpdate)
    }
}