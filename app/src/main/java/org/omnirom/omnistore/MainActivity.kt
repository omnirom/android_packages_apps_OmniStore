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
import android.app.ActivityManager.TaskDescription
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.json.JSONObject
import org.omnirom.omnistore.NetworkUtils.NetworkTaskCallback
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CHECK_UPDATES_OLD
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CHECK_UPDATES_WORKER
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CURRENT_DOWNLOADS
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CURRENT_INSTALLS
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_POST_NOTIFICATION
import org.omnirom.omnistore.databinding.ActivityMainBinding
import java.io.File

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val mDisplayList = mutableListOf<ListItem>()
    private val mAllAppsList = mutableListOf<AppItem>()
    private val TAG = "OmniStore:MainActivity"
    private val mPackageReceiver: PackageReceiver = PackageReceiver()
    private val mInstallReceiver: InstallReceiver = InstallReceiver()
    private val FAKE_DOWNLOAD_ID = Long.MAX_VALUE
    private lateinit var mRecyclerView: RecyclerView
    private var mFetchRunning = false
    private var pendingApp: AppItem? = null
    private lateinit var mPrefs: SharedPreferences
    private lateinit var mBinding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    companion object {
        const val ACTION_START_INSTALL = "start_install"
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

    // when we are active we trigger install right away - else we do it in next onResume
    inner class InstallReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "onReceive " + intent?.action)
            if (intent?.action.equals(ACTION_START_INSTALL)) {
                installPendingPackage()
            }
        }
    }

    private val getNotificationPermissions =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val getStoragePermissions =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingApp?.let { doDownloadApp(it) }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val td = TaskDescription.Builder()
                .setPrimaryColor(getAttrColor(android.R.attr.colorBackground)).build()
            setTaskDescription(td)
        }

        Log.d(TAG, "device = " + DeviceUtils().getProperty(this, "ro.omni.device"))
        mPrefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)

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

        // migrate from old setting
        if (mPrefs.getBoolean(PREF_CHECK_UPDATES_OLD, false)) {
            mPrefs.edit().putBoolean(PREF_CHECK_UPDATES_OLD, false).apply()
            mPrefs.edit().putBoolean(PREF_CHECK_UPDATES_WORKER, true).apply()
            JobUtils().setupWorkManagerJob(this)
        }

        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val installFilter = IntentFilter(ACTION_START_INSTALL)
                ContextCompat.registerReceiver(this, mInstallReceiver, installFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

                val packageFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
                packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED)
                packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
                packageFilter.addDataScheme("package")
                ContextCompat.registerReceiver(this, mPackageReceiver, packageFilter, ContextCompat.RECEIVER_NOT_EXPORTED)

                if (!installPendingPackage()) {
                    refresh()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                try {
                    unregisterReceiver(mInstallReceiver)
                } catch (_: Exception) {
                }
                try {
                    unregisterReceiver(mPackageReceiver)
                } catch (_: Exception) {
                }
            }
        })
        lifecycleScope.launchWhenCreated {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!mPrefs.getBoolean(PREF_POST_NOTIFICATION, false)) {
                    if (applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        mPrefs.edit().putBoolean(PREF_POST_NOTIFICATION, true).apply()
                        getNotificationPermissions.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            if (!hasInstallPermissions()) {
                startActivity(Intent(applicationContext, IntroActivity::class.java))
            }
        }
    }

    private fun hasInstallPermissions(): Boolean {
        return packageManager.canRequestPackageInstalls()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main, menu)
        return true
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
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                pendingApp = null
                doDownloadApp(app)
            } else {
                pendingApp = app
                getStoragePermissions.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        } else {
            pendingApp = null
            doDownloadApp(app)
        }
    }

    private fun doDownloadApp(app: AppItem) {
        if (mFetchRunning) {
            return
        }

        // get a user feedback asap
        setAppItemDownloadState(app, FAKE_DOWNLOAD_ID)

        val checkApp =
            NetworkUtils.CheckAppTask(
                this,
                app,
                object : NetworkTaskCallback {
                    override fun postAction(networkError: Boolean, reponseCode: Int) {
                        if (networkError) {
                            // reset
                            setAppItemDownloadState(app, -1)
                            showNetworkError(reponseCode)
                        } else {
                            val fileName = File(app.getFile()).name
                            val oldDownload =
                                File(this@MainActivity.getExternalFilesDir(null), fileName)
                            if (oldDownload.exists()) {
                                oldDownload.delete()
                            }
                            //request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
                            // now we have a real id
                            viewModel.enqueDownloadApp(app)
                            (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
                        }
                    }
                })
        checkApp.run()
    }

    fun cancelDownloadApp(app: AppItem) {
        if (app.mDownloadId == -1L) {
            return
        }
        viewModel.cancelDownloadApp(app.mDownloadId)
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
        mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())?.let {
            val downloads = JSONObject(it)
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
    }

    private fun updateAllAppStatus() {
        mAllAppsList.forEach { it.updateAppStatus(packageManager) }
    }

    private fun refresh() {
        if (mFetchRunning) {
            return
        }
        Log.d(TAG, "refresh")

        val newAppsList = mutableListOf<AppItem>()
        val fetchApps =
            NetworkUtils.FetchAppsTask(
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

                            val allPkgList = HashSet<String>()
                            mAllAppsList.forEach { allPkgList.add(it.getPackageName()) }
                            // to compare on update check if app list has changed
                            mPrefs.edit().putStringSet(SettingsActivity.PREF_CURRENT_APPS, allPkgList).apply()

                            val updatePkgList = HashSet<String>()
                            mAllAppsList.filter { it.updateAvailable() }
                                .forEach { updatePkgList.add((it.getPackageName())) }
                            // to compare on update check if update available app list has changed
                            mPrefs.edit().putStringSet(SettingsActivity.PREF_UPDATE_APPS, updatePkgList).apply()

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
                val item = mDisplayList.first { it.sortOrder() == SortOrderEnum.UPDATE }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(idx, SeparatorItem(getString(R.string.separator_item_updates)))
            } catch (_: NoSuchElementException) {
            }
            try {
                val item = mDisplayList.first { it.sortOrder() == SortOrderEnum.INSTALLED }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(
                    idx,
                    SeparatorItem(getString(R.string.separator_item_installed))
                )
            } catch (_: NoSuchElementException) {
            }
            try {
                val item = mDisplayList.first { it.sortOrder() == SortOrderEnum.UNINSTALLED }
                val idx = mDisplayList.indexOf(item)
                mDisplayList.add(
                    idx,
                    SeparatorItem(getString(R.string.separator_item_not_installed))
                )
            } catch (_: NoSuchElementException) {
            }

            (mRecyclerView.adapter as AppAdapter).notifyDataSetChanged()
        }
    }

    private fun showNetworkError(reponseCode: Int) {
        val snackbar = Snackbar.make(
            this@MainActivity,
            mBinding.floatingActionButton,
            getString(R.string.network_error, reponseCode),
            Snackbar.LENGTH_LONG
        )
        snackbar.anchorView = mBinding.floatingActionButton
        snackbar.show()
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

    private fun installPendingPackage(): Boolean {
        // dont start installing anything until all downloads are done
        if (isDownloadsRunning()) {
            return false
        }
        mPrefs.getString(PREF_CURRENT_INSTALLS, JSONObject().toString())?.let {
            val installs = JSONObject(it)
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
                try {
                    installPackage(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "installPackage failed uri = " + uri, e)
                }
                return true
            }
        }
        return false
    }

    private fun isDownloadsRunning(): Boolean {
        mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())?.let {
            val downloads = JSONObject(it)
            return downloads.length() != 0
        }
        return false
    }

    private fun removePendingInstall(downloadId: Long) {
        mPrefs.getString(PREF_CURRENT_INSTALLS, JSONObject().toString())?.let {
            val installs = JSONObject(it)
            if (installs.has(downloadId.toString())) {
                installs.remove(downloadId.toString())
                mPrefs.edit().putString(PREF_CURRENT_INSTALLS, installs.toString())
                    .apply()
            }
        }
    }

    private fun getAttrColor(attr: Int): Int {
        val ta = obtainStyledAttributes(intArrayOf(attr))
        val color = ta.getColor(0, 0)
        ta.recycle()
        return color
    }
}
