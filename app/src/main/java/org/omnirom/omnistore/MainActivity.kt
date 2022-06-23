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

import android.Manifest
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.ACTION_START_INSTALL
import org.omnirom.omnistore.Constants.PREF_CHECK_UPDATES
import org.omnirom.omnistore.Constants.PREF_CURRENT_APPS
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS
import org.omnirom.omnistore.Constants.PREF_CURRENT_INSTALLS
import org.omnirom.omnistore.NetworkUtils.NetworkTaskCallback
import org.omnirom.omnistore.databinding.ActivityMainBinding
import java.io.File
import javax.net.ssl.HttpsURLConnection


class MainActivity : AppCompatActivity() {
    private val mDisplayList: ArrayList<ListItem> = ArrayList()
    private val mAllAppsList: ArrayList<AppItem> = ArrayList()
    private val TAG = "OmniStore:MainActivity"
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private val mInstallReceiver: InstallReceiver = InstallReceiver()
    private val REQUEST_STORAGE_PERMS = 0
    private val FAKE_DOWNLOAD_ID = Long.MAX_VALUE
    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mRecyclerView: RecyclerView
    private var mFetchRunning = false
    private var pendingApp: AppItem? = null
    private lateinit var mPrefs: SharedPreferences
    private lateinit var mBinding: ActivityMainBinding

    inner class PackageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action in arrayOf(
                    Intent.ACTION_PACKAGE_ADDED,
                    Intent.ACTION_PACKAGE_CHANGED,
                    Intent.ACTION_PACKAGE_REMOVED
                )
            ) {
                updateAllAppStatus()
                applySortAndFilter()
            }
        }
    }

    // when we are active we trigger install right away - else we do it in next onResume
    inner class InstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action.equals(ACTION_START_INSTALL)) {
                installPendingPackage()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        super.onCreate(savedInstanceState)

        Log.d(TAG, "device = " + DeviceUtils().getProperty(this, "ro.omni.device"))
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)
        setSupportActionBar(mBinding.toolbar)

        mRecyclerView = mBinding.appList
        mRecyclerView.layoutManager = LinearLayoutManager(this)
        mRecyclerView.adapter = AppAdapter(mDisplayList, this)

        mBinding.floatingActionButton.setOnClickListener {
            if (isDownloading()) {
                // TODO alert
                cancelAllDownloads()
            }
            refresh()
        }

        if (mPrefs.getBoolean(PREF_CHECK_UPDATES, false)) {
            JobUtils().scheduleCheckUpdates(this)
        }

        if (!hasInstallPermissions()) {
            startActivity(Intent(this, IntroActivity::class.java))
        }
    }

    private fun hasInstallPermissions(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    override fun onResume() {
        super.onResume()

        val installFilter = IntentFilter(ACTION_START_INSTALL)
        registerReceiver(mInstallReceiver, installFilter)

        val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        packageFilter.addDataScheme("package")
        registerReceiver(mPackageReceiver, packageFilter)

        if (!installPendingPackage()) {
            refresh()
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(mInstallReceiver)
        } catch (e: Exception) {
        }
        try {
            unregisterReceiver(mPackageReceiver)
        } catch (e: Exception) {
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMS && grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
            pendingApp?.let { doDownloadApp(it) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (mFetchRunning) {
            return false
        }
        return when (item.itemId) {
            R.id.menu_item_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    fun downloadApp(app: AppItem) {
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pendingApp = null
            doDownloadApp(app)
        } else {
            pendingApp = app
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), REQUEST_STORAGE_PERMS
            )
        }
    }

    private fun doDownloadApp(app: AppItem) {
        if (mFetchRunning) {
            return
        }

        // get a user feedback asap
        setAppItemDownloadState(app, FAKE_DOWNLOAD_ID)

        val checkApp =
            NetworkUtils().CheckAppTask(
                this,
                app.file!!,
                object : NetworkTaskCallback {
                    override fun postAction(networkError: Boolean, reponseCode: Int) {
                        if (networkError) {
                            // reset
                            setAppItemDownloadState(app, -1)
                            showNetworkError(reponseCode)
                        } else {
                            val url = app.fileUrl(this@MainActivity)

                            val request: DownloadManager.Request =
                                DownloadManager.Request(Uri.parse(url))
                            val fileName = File(app.file).name
                            request.setDestinationInExternalFilesDir(
                                this@MainActivity,
                                null,
                                fileName
                            )
                            val oldDownload =
                                File(this@MainActivity.getExternalFilesDir(null), fileName)
                            if (oldDownload.exists()) {
                                oldDownload.delete()
                            }
                            //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                            // now we have a real id
                            setAppItemDownloadState(app, mDownloadManager.enqueue(request))

                            val serviceIndent =
                                Intent(this@MainActivity, DownloadService::class.java)
                            serviceIndent.action = ACTION_ADD_DOWNLOAD
                            serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_ID, app.mDownloadId)
                            serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_PKG, app.packageName)
                            startForegroundService(serviceIndent)
                        }
                    }
                })
        checkApp.run()
    }

    fun cancelDownloadApp(app: AppItem) {
        if (app.mDownloadId == -1L) {
            return
        }
        mDownloadManager.remove(app.mDownloadId)
        removePendingInstall(app.mDownloadId)
        setAppItemDownloadState(app, -1)
    }

    fun cancelAllDownloads() {
        mDisplayList.filter { it is AppItem && it.mDownloadId != -1L }
            .forEach { cancelDownloadApp((it as AppItem)) }
    }

    fun isDownloading(): Boolean {
        return mDisplayList.filter { it is AppItem && it.mDownloadId != -1L }
            .isNotEmpty()
    }

    fun startProgress() {
        mBinding.progress.visibility = View.VISIBLE
    }

    fun stopProgress() {
        mBinding.progress.visibility = View.GONE
    }

    fun handleInstallComplete(downloadId: Long?) {
        val list = mDisplayList.filter { it is AppItem && it.mDownloadId == downloadId }
        if (list.size == 1) {
            val app = (list.first() as AppItem)
            setAppItemDownloadState(app, -1)
        }
    }

    private fun syncRunningDownloads() {
        val stats: String? = mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
        val downloads = JSONObject(stats!!)
        Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
        for (id in downloads.keys()) {
            val pkg = downloads.get(id).toString()
            val dl = mAllAppsList.filter { it.packageName == pkg }
            if (dl.size == 1) {
                dl.first().mDownloadId = id.toLong()
                Log.d(TAG, "set downloadId = " + id + " to " + dl.first())
            }
        }
    }

    private fun updateAllAppStatus() {
        mAllAppsList.forEach { it.updateAppStatus(packageManager) }
    }

    private fun refresh() {
        if (mFetchRunning) {
            return
        }
        Log.d(TAG, "refresh")

        val newAppsList: ArrayList<AppItem> = ArrayList()
        val fetchApps =
            NetworkUtils().FetchAppsTask(
                this,
                {
                    mFetchRunning = true
                    startProgress()
                },
                object : NetworkTaskCallback {
                    override fun postAction(networkError: Boolean, reponseCode: Int) {
                        if (networkError) {
                            showNetworkError(reponseCode)
                        } else {
                            mAllAppsList.clear()
                            mAllAppsList.addAll(newAppsList)
                            updateAllAppStatus()
                            syncRunningDownloads()

                            val pkgList = HashSet<String>()
                            mAllAppsList.forEach { pkgList.add(it.packageName!!) }
                            // to compare on update check if app list has changed
                            mPrefs.edit().putStringSet(PREF_CURRENT_APPS, pkgList).apply()

                            applySortAndFilter()
                        }
                        stopProgress()
                        mFetchRunning = false
                    }
                },
                newAppsList
            )
        fetchApps.run()
    }

    private fun showGrouped() {
        synchronized(this@MainActivity) {
            mDisplayList.clear()
            mDisplayList.addAll(mAllAppsList)

            mDisplayList.sortBy { it.title() }
            mDisplayList.sortBy { it.sortOrder() }

            try {
                val item = mDisplayList.first { it.sortOrder() == 0 }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(idx, SeparatorItem(getString(R.string.separator_item_updates)))
            } catch (e: NoSuchElementException) {
            }
            try {
                val item = mDisplayList.first { it.sortOrder() == 1 }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(
                    idx,
                    SeparatorItem(getString(R.string.separator_item_installed))
                )
            } catch (e: NoSuchElementException) {
            }
            try {
                val item = mDisplayList.first { it.sortOrder() == 2 }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(
                    idx,
                    SeparatorItem(getString(R.string.separator_item_not_installed))
                )
            } catch (e: NoSuchElementException) {
            }

            (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
        }
    }

    private fun showNetworkError(reponseCode: Int) {
        val themeContext = ContextThemeWrapper(this, R.style.Theme_AlertDialog)
        val builder = AlertDialog.Builder(themeContext)
        builder.setTitle(getString(R.string.dialog_title_network_error))
        if (reponseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
            builder.setMessage(getString(R.string.dialog_message_network_error_not_found))
        } else {
            builder.setMessage(getString(R.string.dialog_message_network_error))
        }
        builder.setPositiveButton(android.R.string.ok, null)
        builder.create().show()
    }

    private fun applySortAndFilter() {
        showGrouped()
    }

    private fun setAppItemDownloadState(app: AppItem, id: Long) {
        app.mDownloadId = id
        (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
    }

    private fun installPackage(uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(
            uri, "application/vnd.android.package-archive"
        )
        intent.flags = (Intent.FLAG_ACTIVITY_NEW_TASK
                or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(intent)
    }

    private fun uninstallPackage(pkg: String) {
        val intent = Intent(Intent.ACTION_DELETE)
        intent.data = Uri.parse("package:" + pkg)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun installPendingPackage(): Boolean {
        // dont start installing anything until all downloads are done
        if (isDownloadsRunning()) {
            return false
        }
        val stats: String? =
            mPrefs.getString(PREF_CURRENT_INSTALLS, JSONObject().toString())
        val installs = JSONObject(stats!!)
        if (installs.length() != 0) {
            // we have installs pending so start with the first one - we will come back in
            // onResume when its done and come back here
            Log.d(TAG, "CURRENT_INSTALLS = " + installs)
            val first = installs.names()?.get(0) as String
            val data = installs[first] as JSONObject
            val uri = Uri.parse(data.get("uri") as String)
            // this means when we press back or home we will not asked again for this install
            // and this is a not really an unintended behaviour - back or home == cancel
            installs.remove(first)
            mPrefs.edit().putString(PREF_CURRENT_INSTALLS, installs.toString())
                .apply()
            // stop the progress for this
            handleInstallComplete(first.toLong())
            installPackage(uri)
            return true
        }
        return false
    }

    private fun isDownloadsRunning(): Boolean {
        val stats: String? = mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
        val downloads = JSONObject(stats!!)
        return downloads.length() != 0
    }

    private fun removePendingInstall(downloadId: Long) {
        val stats: String? =
            mPrefs.getString(PREF_CURRENT_INSTALLS, JSONObject().toString())
        val installs = JSONObject(stats!!)
        if (installs.has(downloadId.toString())) {
            installs.remove(downloadId.toString())
            mPrefs.edit().putString(PREF_CURRENT_INSTALLS, installs.toString())
                .apply()
        }
    }
}
