package org.omnirom.omnistore

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.APPS_BASE_URI
import org.omnirom.omnistore.Constants.PREF_CHECK_UPDATES
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS


class MainActivity : AppCompatActivity() {
    private val mAppsList: ArrayList<AppItem> = ArrayList()
    private val TAG = "OmniStore:MainActivity"
    private val mDownloadReceiver: DownloadReceiver = DownloadReceiver()
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mRecyclerView: RecyclerView
    private var mNetworkConnected = false;
    private val mNetworkCallback = NetworkCallback()

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

    inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "NetworkCallback onAvailable")
            mNetworkConnected = true
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "NetworkCallback onLost")
            mNetworkConnected = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "device = " + DeviceUtils().getProperty(this, "ro.omni.device"));
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        setContentView(R.layout.activity_main)

        mRecyclerView = findViewById<RecyclerView>(R.id.app_list)
        mRecyclerView.layoutManager = LinearLayoutManager(this)
        mRecyclerView.adapter = AppAdapter(mAppsList, this)

        val downloadFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(mDownloadReceiver, downloadFilter)

        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addDataScheme("package")
        registerReceiver(mPackageReceiver, packageFilter)

        findViewById<FloatingActionButton>(R.id.floatingActionButton).setOnClickListener {
            if (isDownloading()) {
                // TODO alert
                cancelAllDownloads()
            }
            refresh()
        }
        refresh()

        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(this)
        if (prefs.getBoolean(PREF_CHECK_UPDATES, false)) {
            JobUtils().scheduleCheckUpdates(this)
        }
    }

    override fun onResume() {
        super.onResume()

        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(mNetworkCallback)
    }

    override fun onPause() {
        super.onPause()
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.unregisterNetworkCallback(mNetworkCallback)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mDownloadReceiver)
        unregisterReceiver(mPackageReceiver)
    }

    fun downloadApp(app: AppItem) {
        if (app.fileUrl() == null) {
            Log.d(TAG, "downloadApp no fileUrl")
            return
        }
        if (!mNetworkConnected) {
            Log.d(TAG, "downloadApp no network")
            return
        }
        val url: String = app.fileUrl()!!
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, app.file())
        //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        //request.setNotificationVisibility()
        app.mDownloadId = mDownloadManager.enqueue(request)
        (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()

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
        (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
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
        findViewById<FrameLayout>(R.id.progress).visibility = View.VISIBLE
    }

    fun stopProgress() {
        findViewById<FrameLayout>(R.id.progress).visibility = View.GONE
    }

    fun handleDownloadComplete(downloadId: Long?) {
        val list = mAppsList.filter { it.mDownloadId == downloadId }
        if (list.size == 1) {
            list.first().mDownloadId = -1
        }
        (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
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
        if (mNetworkConnected) {
            Log.d(TAG, "refresh no network")
            return
        }
        val newAppsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(this, Runnable { startProgress() }, Runnable {
                synchronized(this@MainActivity) {
                    mAppsList.clear()
                    mAppsList.addAll(newAppsList)
                    updateAllAppStatus()
                    syncRunningDownloads()
                    this@MainActivity.runOnUiThread(Runnable {
                        (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
                        stopProgress()
                    })
                }
            }, newAppsList)
        fetchApps.execute()
    }
}
