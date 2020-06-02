package org.omnirom.omnistore

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import org.json.JSONArray
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.APPS_BASE_URI
import org.omnirom.omnistore.Constants.APPS_LIST_URI
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection


class MainActivity : AppCompatActivity() {

    private val mAppsList: ArrayList<AppItem> = ArrayList()
    private val TAG = "OmniStore:MainActivity"
    private val HTTP_READ_TIMEOUT = 30000
    private val HTTP_CONNECTION_TIMEOUT = 30000
    private val mDownloadReceiver: DownloadReceiver = DownloadReceiver()
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private val REQUEST_ERMISSION = 0
    var mInstallEnabled = false
    private var mDownloadManager: DownloadManager? = null

    inner class FetchAppsTask : AsyncTask<String, Int, Int>() {
        val newAppsList: ArrayList<AppItem> = ArrayList()
        override fun onPreExecute() {
            super.onPreExecute()
            startProgress()
        }

        override fun doInBackground(vararg params: String?): Int {
            val appListData: String? = downloadUrlMemoryAsString(APPS_LIST_URI)
            if (appListData != null) {
                val apps = JSONArray(appListData)
                for (i in 0 until apps.length()) {
                    val app = apps.getJSONObject(i);
                    val appData = AppItem(app)
                    if (appData.isValied()) {
                        newAppsList.add(appData)
                    } else {
                        Log.i(TAG, "ignore app " + app.toString())
                    }
                }
            }
            newAppsList.sortBy { it -> it.title() }
            return 0
        }

        override fun onPostExecute(result: Int?) {
            super.onPostExecute(result)
            synchronized(this@MainActivity) {
                mAppsList.clear()
                mAppsList.addAll(newAppsList)
                updateAllAppStatus()
                syncRunningDownloads()
                this@MainActivity.runOnUiThread(java.lang.Runnable {
                    (app_list.adapter as AppAdapter).notifyDataSetChanged()
                    stopProgress()
                })
            }
        }
    }

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
                val fetchApps = FetchAppsTask()
                fetchApps.execute()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            mInstallEnabled = true;
        }

        app_list.layoutManager = LinearLayoutManager(this)
        app_list.adapter = AppAdapter(mAppsList, this)
        (app_list.adapter as AppAdapter).mActivity = this

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
            val fetchApps = FetchAppsTask()
            fetchApps.execute()
        }
        if (!mInstallEnabled) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), REQUEST_ERMISSION
            )
        } else {
            val fetchApps = FetchAppsTask()
            fetchApps.execute()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_ERMISSION && grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
            mInstallEnabled = true;
            val fetchApps = FetchAppsTask()
            fetchApps.execute()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(mDownloadReceiver)
        unregisterReceiver(mPackageReceiver)
    }

    private fun setupHttpsRequest(urlStr: String): HttpsURLConnection? {
        val url: URL
        try {
            url = URL(urlStr)
            val urlConnection = url.openConnection() as HttpsURLConnection
            urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT)
            urlConnection.setReadTimeout(HTTP_READ_TIMEOUT)
            urlConnection.setRequestMethod("GET")
            urlConnection.setDoInput(true)
            urlConnection.connect()
            val code: Int = urlConnection.getResponseCode()
            if (code != HttpsURLConnection.HTTP_OK) {
                Log.d(TAG, "response: " + code)
                return null
            }
            return urlConnection
        } catch (e: Exception) {
            Log.e(TAG, "setupHttpsRequest", e)
            return null
        }
    }

    private fun downloadUrlMemoryAsString(url: String): String? {
        Log.d(TAG, "download: " + url)
        var urlConnection: HttpsURLConnection? = null
        return try {
            urlConnection = setupHttpsRequest(url)
            if (urlConnection == null) {
                return null
            }
            val input: InputStream = urlConnection.inputStream
            val byteArray = ByteArrayOutputStream()
            var byteInt: Int = 0
            while (input.read().also({ byteInt = it }) >= 0) {
                byteArray.write(byteInt)
            }
            val bytes: ByteArray = byteArray.toByteArray() ?: return null
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: java.lang.Exception) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "downloadUrlMemoryAsString", e)
            null
        } finally {
            urlConnection?.disconnect()
        }
    }

    fun downloadApp(app: AppItem?) {
        if (!mInstallEnabled) {
            return
        }
        val url: String = APPS_BASE_URI + app?.file()
        val request: DownloadManager.Request = DownloadManager.Request(Uri.parse(url))
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, app?.file())
        //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        //request.setNotificationVisibility()
        app?.mDownloadId = mDownloadManager?.enqueue(request)
        (app_list.adapter as AppAdapter).notifyDataSetChanged()

        val serviceIndent = Intent(this, DownloadService::class.java)
        serviceIndent.action = ACTION_ADD_DOWNLOAD
        serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_ID, app?.mDownloadId)
        serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_PKG, app?.pkg())
        startForegroundService(serviceIndent)
    }

    fun cancelDownloadApp(app: AppItem?) {
        if (app?.mDownloadId == -1L) {
            return
        }
        mDownloadManager?.remove(app?.mDownloadId as Long)
        app?.mDownloadId = -1L
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
        mAppsList.forEach { updateAppStatus(it) }
    }

    private fun updateAppStatus(app: AppItem) {
        try {
            val pkg = app.pkg()
            val pkgInfo = packageManager.getPackageInfo(
                pkg!!,
                0
            )
            app.mVersionCode = pkgInfo.versionCode
            app.mVersionName = pkgInfo.versionName
            val enabled: Int =
                packageManager.getApplicationEnabledSetting(pkg)
            if (enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                    enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                app.mInstalled = 0
            } else {
                app.mInstalled = 1
            }
        } catch (e: Exception) {
            app.mInstalled = -1
            app.mVersionCode = -1
            app.mVersionName = "unknown"
        }
    }
}
