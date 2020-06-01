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
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS

class DownloadService : Service() {
    private val TAG = "OmniStore:DownloadService"
    private val NOTIFICATION_CHANNEL_ID = "org.omnirom.omnistore.notification"
    private val NOTIFICATION_ID = Int.MAX_VALUE;

    private var mDownloadReceiver: DownloadReceiver? = null
    private var mDownloadList: ArrayList<Long> = ArrayList()
    private var mDownloadManager: DownloadManager? = null

    override fun onCreate() {
        super.onCreate()
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        createNotificationChannel()
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

                    val prefs:SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
                    val stats:String? = prefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                    val downloads = JSONObject(stats)
                    downloads.remove(downloadId.toString())
                    prefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString()).commit()
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
            startForeground(NOTIFICATION_ID, showDummyNotification(this))
            val id = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1)
            val pkg = intent?.getStringExtra(EXTRA_DOWNLOAD_PKG)

            Log.d(TAG, "ADD_DOWNLOAD DOWNLOAD_ID = " + id + " DOWNLOAD_PKG = " + pkg)

            if (id != -1L) {
                if (mDownloadReceiver == null) {
                    mDownloadReceiver = DownloadReceiver()
                    val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                    registerReceiver(mDownloadReceiver, downloadFilter)
                }
                val prefs:SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
                val stats:String? = prefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                val downloads = JSONObject(stats)
                downloads.put(id.toString(), pkg)
                prefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString()).commit()
                Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
                mDownloadList.add(id as Long)
            }
        }
        if (intent?.action.equals(ACTION_CANCEL_DOWNLOAD)) {
            Log.d(TAG, "CANCEL_DOWNLOAD")
            cancelAllDownloads()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        val prefs:SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().remove(PREF_CURRENT_DOWNLOADS).commit()

        if (mDownloadReceiver != null) {
            unregisterReceiver(mDownloadReceiver)
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun showDummyNotification(context: Context): Notification? {
        val notification = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(resources.getString(R.string.app_name))
            .setContentText("Downloading...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setOngoing(true)

        val cancelIntent = Intent(this, DownloadService::class.java)
        cancelIntent.action = ACTION_CANCEL_DOWNLOAD
        val cancelPendingIntent: PendingIntent = PendingIntent.getService(
            context,
            cancelIntent.hashCode(), cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        notification.addAction(
            R.drawable.ic_cancel,
            "Stop",
            cancelPendingIntent
        )

        return notification.build()
    }

    private fun createNotificationChannel() {
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name: CharSequence = "Download"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            name,
            importance
        )
        notificationManager.createNotificationChannel(channel)
    }

    fun handleDownloadComplete(downloadId: Long) {
        var uri: Uri? = mDownloadManager?.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            // includes also cancel
            return
        }
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            uri
            , "application/vnd.android.package-archive"
        )
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    fun cancelAllDownloads() {
        mDownloadList.forEach { cancelDownloadApp(it) }
    }

    fun cancelDownloadApp(downloadId: Long) {
        mDownloadManager?.remove(downloadId)
    }
}