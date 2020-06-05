package org.omnirom.omnistore

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.ACTION_CANCEL_DOWNLOAD
import org.omnirom.omnistore.Constants.ACTION_CHECK_UPDATES
import org.omnirom.omnistore.Constants.EXTRA_DOWNLOAD_ID
import org.omnirom.omnistore.Constants.EXTRA_DOWNLOAD_PKG
import org.omnirom.omnistore.Constants.NOTIFICATION_CHANNEL_PROGRESS
import org.omnirom.omnistore.Constants.NOTIFICATION_CHANNEL_UPDATE
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS


class DownloadService : Service() {
    private val TAG = "OmniStore:DownloadService"

    private val NOTIFICATION_PROGRESS_ID = Int.MAX_VALUE;
    private val NOTIFICATION_UPDATES_ID = Int.MAX_VALUE - 1;

    private var mDownloadReceiver: DownloadReceiver? = null
    private var mDownloadList: ArrayList<Long> = ArrayList()
    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mWakeLock: WakeLock

    override fun onCreate() {
        super.onCreate()
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
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

                    val prefs: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(context)
                    val stats: String? =
                        prefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                    val downloads = JSONObject(stats!!)
                    downloads.remove(downloadId.toString())
                    prefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString())
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
        mWakeLock.acquire()
        try {
            if (intent?.action.equals(ACTION_ADD_DOWNLOAD)) {
                startForeground(NOTIFICATION_PROGRESS_ID, showProgressNotification(this))
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
                    val prefs: SharedPreferences =
                        PreferenceManager.getDefaultSharedPreferences(this)
                    val stats: String? =
                        prefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                    val downloads = JSONObject(stats!!)
                    downloads.put(id.toString(), pkg)
                    prefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString())
                        .commit()
                    Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
                    mDownloadList.add(id as Long)
                }
            } else if (intent?.action.equals(ACTION_CANCEL_DOWNLOAD)) {
                Log.d(TAG, "CANCEL_DOWNLOAD")
                cancelAllDownloads()

            } else if (intent?.action.equals(ACTION_CHECK_UPDATES)) {
                Log.d(TAG, "ACTION_CHECK_UPDATES")
                checkForUpdates(this);
            }
        } finally {
            mWakeLock.release()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        prefs.edit().remove(PREF_CURRENT_DOWNLOADS).commit()

        if (mDownloadReceiver != null) {
            unregisterReceiver(mDownloadReceiver)
            stopForeground(true)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun showProgressNotification(context: Context): Notification? {
        val notification = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_PROGRESS
        )
            .setContentTitle("Download and install")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setOngoing(true)
            .setColor(context.resources.getColor(R.color.omni_logo_color))

        val cancelIntent = Intent(context, DownloadService::class.java)
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

    private fun showUpdatesNotification(context: Context, message: String): Notification? {
        val notification = NotificationCompat.Builder(
            context,
            NOTIFICATION_CHANNEL_UPDATE
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

    fun checkForUpdates(context: Context) {
        if (!Constants.isNetworkConnected) {
            Log.d(TAG, "checkForUpdates no network")
            return
        }
        val newAppsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(context, Runnable { }, Runnable {
                newAppsList.forEach { it.updateAppStatus(context.packageManager) }
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
                    val notification = showUpdatesNotification(context, message)
                    val notificationManager =
                        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(NOTIFICATION_UPDATES_ID)
                    notificationManager.notify(NOTIFICATION_UPDATES_ID, notification)
                }
            }, newAppsList)
        fetchApps.execute()
    }
}