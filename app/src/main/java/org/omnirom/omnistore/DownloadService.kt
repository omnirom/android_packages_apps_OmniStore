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

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.ACTION_CANCEL_DOWNLOAD
import org.omnirom.omnistore.Constants.EXTRA_DOWNLOAD_ID
import org.omnirom.omnistore.Constants.EXTRA_DOWNLOAD_PKG
import org.omnirom.omnistore.Constants.NOTIFICATION_CHANNEL_PROGRESS
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS


class DownloadService : Service() {
    private val TAG = "OmniStore:DownloadService"

    private val NOTIFICATION_PROGRESS_ID = Int.MAX_VALUE;

    private var mDownloadReceiver: DownloadReceiver? = null
    private var mDownloadList: ArrayList<Long> = ArrayList()
    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    }

    inner class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != -1L) {
                    Log.d(
                        TAG,
                        "ACTION_DOWNLOAD_COMPLETE DOWNLOAD_ID = " + downloadId + " " + mDownloadList
                    )
                    handleDownloadComplete(downloadId as Long)
                    mDownloadList.remove(downloadId)

                    val stats: String? =
                        mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                    val downloads = JSONObject(stats!!)
                    downloads.remove(downloadId.toString())
                    mPrefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString())
                        .commit()
                    Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
                    if (mDownloadList.isEmpty()) {
                        Log.d(TAG, "kill me")
                        this@DownloadService.stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action.equals(ACTION_ADD_DOWNLOAD)) {
            startForeground(NOTIFICATION_PROGRESS_ID, showProgressNotification())
            val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
            val pkg = intent?.getStringExtra(EXTRA_DOWNLOAD_PKG)

            Log.d(TAG, "ADD_DOWNLOAD DOWNLOAD_ID = " + id + " DOWNLOAD_PKG = " + pkg)

            if (id != -1L) {
                if (mDownloadReceiver == null) {
                    mDownloadReceiver = DownloadReceiver()
                    val downloadFilter =
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    registerReceiver(mDownloadReceiver, downloadFilter)
                }
                val stats: String? =
                    mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                val downloads = JSONObject(stats!!)
                downloads.put(id.toString(), pkg)
                mPrefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString())
                    .commit()
                Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
                mDownloadList.add(id as Long)
            }
        } else if (intent?.action.equals(ACTION_CANCEL_DOWNLOAD)) {
            Log.d(TAG, "CANCEL_DOWNLOAD")
            cancelAllDownloads()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        mPrefs.edit().remove(PREF_CURRENT_DOWNLOADS).commit()

        if (mDownloadReceiver != null) {
            unregisterReceiver(mDownloadReceiver)
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun showProgressNotification(): Notification {
        val notification = NotificationCompat.Builder(
            this,
            NOTIFICATION_CHANNEL_PROGRESS
        )
            .setContentTitle(getString(R.string.notification_download_title))
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setOngoing(true)
            .setColor(resources.getColor(R.color.omni_logo_color, null))

        val cancelIntent = Intent(this, DownloadService::class.java)
        cancelIntent.action = ACTION_CANCEL_DOWNLOAD
        val cancelPendingIntent: PendingIntent = PendingIntent.getService(
            this,
            cancelIntent.hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        notification.addAction(
            R.drawable.ic_cancel,
            getString(R.string.notificatioon_action_stop),
            cancelPendingIntent
        )

        return notification.build()
    }

    private fun handleDownloadComplete(downloadId: Long) {
        var uri: Uri? = mDownloadManager.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            // includes also cancel
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            uri, "application/vnd.android.package-archive"
        )
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    private fun cancelAllDownloads() {
        mDownloadList.forEach { cancelDownloadApp(it) }
    }

    private fun cancelDownloadApp(downloadId: Long) {
        mDownloadManager.remove(downloadId)
    }
}
