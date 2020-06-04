package org.omnirom.omnistore

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.APPS_BASE_URI
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS


class MainActivity : AppCompatActivity() {
    private val mAppsList: ArrayList<AppItem> = ArrayList()
    private val TAG = "OmniStore:MainActivity"
    private val mDownloadReceiver: DownloadReceiver = DownloadReceiver()
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private val REQUEST_ERMISSION = 0
    var mInstallEnabled = false
    private lateinit var mDownloadManager: DownloadManager

    inner class DownloadReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
                val downloadId = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (downloadId == -1L) {
                    return
                }
                handleDownloadComplete(downloadId)
            }
        }
    }

    inner class PackageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action in arrayOf(
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_CHANGED,
                    Intent.ACTION_PACKAGE_REMOVED
                )
            ) {
                refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "device = " + DeviceUtils().getProperty(this, "ro.omni.device"));
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            mInstallEnabled = true;
        }

        app_list.layoutManager = LinearLayoutManager(this)
        app_list.adapter = AppAdapter(mAppsList, this)

        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(mDownloadReceiver, downloadFilter)

        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addDataScheme("package")
        registerReceiver(mPackageReceiver, packageFilter)

        floatingActionButton.setOnClickListener {
            if (isDownloading()) {
                // TODO alert
                cancelAllDownloads()
            }
            refresh()
        }
        if (!mInstallEnabled) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), REQUEST_ERMISSION
            )
        } else {
            refresh()
        }

        DeviceUtils().setAlarm(this)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)

        val appBarSwitch = menu!!.findItem(R.id.app_bar_switch)
        val switchItem = appBarSwitch.actionView.findViewById<Switch>(R.id.switch_item)

        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        switchItem.isChecked = prefs.getBoolean(Constants.PREF_ALARM_ACTIVE, false)

        switchItem.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) DeviceUtils().setAlarm(this)
            else DeviceUtils().cancelAlarm(this)
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_ERMISSION && grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
            mInstallEnabled = true;
            refresh()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mDownloadReceiver)
        unregisterReceiver(mPackageReceiver)
    }

    fun downloadApp(app: AppItem) {
        if (!mInstallEnabled) {
            return
        }
        val url: String = APPS_BASE_URI + app.file()
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, app.file())
        //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        //request.setNotificationVisibility()
        app.mDownloadId = mDownloadManager.enqueue(request)
        (app_list.adapter as AppAdapter).notifyDataSetChanged()

        val serviceIndent = Intent(this, DownloadService::class.java)
        serviceIndent.action = ACTION_ADD_DOWNLOAD
        serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_ID, app.mDownloadId)
        serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_PKG, app.pkg())
        startForegroundService(serviceIndent)
    }

    fun cancelDownloadApp(app: AppItem) {
        if (app.mDownloadId == -1L) {
            return
        }
        mDownloadManager.remove(app.mDownloadId)
        app.mDownloadId = -1L
        (app_list.adapter as AppAdapter).notifyDataSetChanged()
    }

    fun cancelAllDownloads() {
        mAppsList.filter { it.mDownloadId != -1L }.forEach { cancelDownloadApp(it) }
    }

    fun isDownloading(): Boolean {
        return mAppsList.filter { it.mDownloadId != -1L }.isNotEmpty()
    }

    fun downloadAll() {
        mAppsList.filter { it.mDownloadId == -1L }.forEach { downloadApp(it) }
    }

    fun startProgress() {
        progress.visibility = View.VISIBLE
    }

    fun stopProgress() {
        progress.visibility = View.GONE
    }

    fun handleDownloadComplete(downloadId: Long?) {
        val list = mAppsList.filter { it.mDownloadId == downloadId }
        if (list.size == 1) {
            list.first().mDownloadId = -1
        }
        (app_list.adapter as AppAdapter).notifyDataSetChanged()
    }

    private fun syncRunningDownloads() {
        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        val stats: String? = prefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
        val downloads = JSONObject(stats!!)
        Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
        for (id in downloads.keys()) {
            val pkg = downloads.get(id).toString()
            val dl = mAppsList.filter { it.pkg().equals(pkg) }
            if (dl.size == 1) {
                dl.first().mDownloadId = id.toLong()
                Log.d(TAG, "set downloadId = " + id + " to " + dl.first())
            }
        }
    }

    private fun updateAllAppStatus() {
        mAppsList.forEach { it.updateAppStatus(packageManager) }
    }

    private fun refresh() {
        val newAppsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(this, Runnable { startProgress() }, Runnable {
                synchronized(this@MainActivity) {
                    mAppsList.clear()
                    mAppsList.addAll(newAppsList)
                    updateAllAppStatus()
                    syncRunningDownloads()
                    this@MainActivity.runOnUiThread(Runnable {
                        (app_list.adapter as AppAdapter).notifyDataSetChanged()
                        stopProgress()
                    })
                }
            }, newAppsList)
        fetchApps.execute()
    }
}
