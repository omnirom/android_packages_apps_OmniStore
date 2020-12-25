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
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import org.omnirom.omnistore.Constants.ACTION_ADD_DOWNLOAD
import org.omnirom.omnistore.Constants.PREF_CHECK_UPDATES
import org.omnirom.omnistore.Constants.PREF_CURRENT_APPS
import org.omnirom.omnistore.Constants.PREF_CURRENT_DOWNLOADS
import org.omnirom.omnistore.Constants.PREF_SHOW_INTRO
import org.omnirom.omnistore.Constants.PREF_VIEW_GROUPS
import org.omnirom.omnistore.NetworkUtils.NetworkTaskCallback
import java.io.File


class MainActivity : AppCompatActivity() {
    private val mDisplayList: ArrayList<ListItem> = ArrayList()
    private val mAllAppsList: ArrayList<AppItem> = ArrayList()
    private val TAG = "OmniStore:MainActivity"
    private val mDownloadReceiver: DownloadReceiver = DownloadReceiver()
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private val REQUEST_ERMISSION = 0
    private val FAKE_DOWNLOAD_ID = Long.MAX_VALUE
    private lateinit var mDownloadManager: DownloadManager
    private lateinit var mRecyclerView: RecyclerView
    private var mViewGroups = true
    private var mInitialRefresh = false

    //private lateinit var mFilterMenu: MenuItem
    private var mFetchRunning = false
    private var pendingApp: AppItem? = null
    private lateinit var mPrefs: SharedPreferences

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
                updateAllAppStatus()
                applySortAndFilter()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "device = " + DeviceUtils().getProperty(this, "ro.omni.device"));
        mDownloadManager = this.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        setContentView(R.layout.activity_main)

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this)

        mRecyclerView = findViewById<RecyclerView>(R.id.app_list)
        mRecyclerView.layoutManager = LinearLayoutManager(this)
        mRecyclerView.adapter = AppAdapter(mDisplayList, this)

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

        if (mPrefs.getBoolean(PREF_CHECK_UPDATES, false)) {
            JobUtils().scheduleCheckUpdates(this)
        }
        mViewGroups = mPrefs.getBoolean(PREF_VIEW_GROUPS, true)

        if (!mPrefs.getBoolean(PREF_SHOW_INTRO, false)) {
            mPrefs.edit().putBoolean(PREF_SHOW_INTRO, true).commit();
            startActivity(Intent(this, IntroActivity::class.java))
        } else {
            mInitialRefresh = true
            refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!mInitialRefresh){
            refresh()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        /*mFilterMenu = menu!!.findItem(R.id.menu_item_filter)
        if (mViewGroups) {
            mFilterMenu.icon = resources.getDrawable(R.drawable.ic_star_white)
        } else {
            mFilterMenu.icon = resources.getDrawable(R.drawable.ic_star_outline_white)
        }*/
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String?>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_ERMISSION && grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
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
            R.id.menu_item_filter -> {
                if (mViewGroups) {
                    showAll()
                    //mFilterMenu.icon = resources.getDrawable(R.drawable.ic_star_outline_white)
                } else {
                    showGrouped()
                    //mFilterMenu.icon = resources.getDrawable(R.drawable.ic_star_white)
                }
                mPrefs.edit().putBoolean(PREF_VIEW_GROUPS, mViewGroups).commit()
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
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            pendingApp = null
            doDownloadApp(app)
        } else {
            pendingApp = app
            requestPermissions(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), REQUEST_ERMISSION
            )
        }
    }

    private fun doDownloadApp(app: AppItem) {
        if (mFetchRunning) {
            return
        }
        if (app.fileUrl(this) == null) {
            Log.d(TAG, "downloadApp no fileUrl")
            return
        }
        // get a user feedback asap
        setAppItemDownloadState(app, FAKE_DOWNLOAD_ID)

        val url = app.fileUrl(this)!!
        val checkApp =
            NetworkUtils().CheckAppTask(
                url,
                object : NetworkTaskCallback {
                    override fun postAction(networkError: Boolean) {
                        if (networkError) {
                            // reset
                            setAppItemDownloadState(app, -1)
                            showNetworkError(url)
                        } else {
                            val request: DownloadManager.Request =
                                DownloadManager.Request(Uri.parse(url))
                            val fileName = File(app.file()).name
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
                            serviceIndent.putExtra(Constants.EXTRA_DOWNLOAD_PKG, app.pkg())
                            startForegroundService(serviceIndent)
                        }
                    }
                })
        checkApp.execute()
    }

    fun cancelDownloadApp(app: AppItem) {
        if (app.mDownloadId == -1L) {
            return
        }
        mDownloadManager.remove(app.mDownloadId)

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
        findViewById<FrameLayout>(R.id.progress).visibility = View.VISIBLE
    }

    fun stopProgress() {
        findViewById<FrameLayout>(R.id.progress).visibility = View.GONE
    }

    fun handleDownloadComplete(downloadId: Long?) {
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
            val dl = mAllAppsList.filter { it.pkg() == pkg }
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
                Runnable {
                    mFetchRunning = true
                    startProgress()
                },
                object : NetworkTaskCallback {
                    override fun postAction(networkError: Boolean) {
                        if (networkError) {
                            showNetworkError(Constants.getAppsQueryUri(this@MainActivity))
                        } else {
                            mAllAppsList.clear()
                            mAllAppsList.addAll(newAppsList)
                            updateAllAppStatus()
                            syncRunningDownloads()

                            val pkgList = HashSet<String>()
                            mAllAppsList.forEach { pkgList.add(it.pkg()) }
                            // to compare on update check if app list has changed
                            mPrefs.edit().putStringSet(PREF_CURRENT_APPS, pkgList).commit()

                            applySortAndFilter()
                        }
                        stopProgress()
                        mFetchRunning = false
                    }
                },
                newAppsList
            )
        fetchApps.execute()
    }

    private fun showGrouped() {
        synchronized(this@MainActivity) {
            mDisplayList.clear()
            mDisplayList.addAll(mAllAppsList)

            mDisplayList.sortBy { it.title() }
            mDisplayList.sortBy { it.sortOrder() }

            try {
                var item = mDisplayList.first { it.sortOrder() == 0 }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(idx, SeparatorItem(getString(R.string.separator_item_updates)))
            } catch (e: NoSuchElementException) {
            }
            try {
                var item = mDisplayList.first { it.sortOrder() == 1 }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(idx, SeparatorItem(getString(R.string.separator_item_installed)))
            } catch (e: NoSuchElementException) {
            }
            try {
                var item = mDisplayList.first { it.sortOrder() == 2 }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(
                    idx,
                    SeparatorItem(getString(R.string.separator_item_not_installed))
                )
            } catch (e: NoSuchElementException) {
            }

            (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
        }
        mViewGroups = true
    }

    private fun showAll() {
        synchronized(this@MainActivity) {
            mDisplayList.clear()
            mDisplayList.addAll(mAllAppsList)
            mDisplayList.sortBy { it.title() }
            (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
        }
        mViewGroups = false
    }

    private fun showNetworkError(url: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.dialog_title_network_error))
        builder.setMessage(getString(R.string.dialog_message_network_error));
        builder.setPositiveButton(android.R.string.ok, null)
        builder.create().show()
    }

    private fun applySortAndFilter() {
        if (mViewGroups) {
            showGrouped()
        } else {
            showAll()
        }
    }

    private fun setAppItemDownloadState(app: AppItem, id: Long) {
        app.mDownloadId = id
        (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
    }
}
