package org.omnirom.omnistore

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext val applicationContext: Context) :
    ViewModel() {
    private val TAG = "OmniStore:MainViewModel"

    private var mDownloadReceiver = DownloadReceiver()
    private var mDownloadList = mutableListOf<Long>()
    private var mDownloadManager: DownloadManager =
        applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private var mPrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    init {
        mDownloadReceiver = DownloadReceiver()
        val downloadFilter =
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        applicationContext.registerReceiver(mDownloadReceiver, downloadFilter)
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

                    mDownloadList.remove(downloadId)

                    mPrefs.getString(Constants.PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                        ?.let {
                            val downloads = JSONObject(it)
                            if (downloads.has(downloadId.toString())) {
                                val pkg = downloads.get(downloadId.toString())
                                downloads.remove(downloadId.toString())
                                mPrefs.edit().putString(
                                    Constants.PREF_CURRENT_DOWNLOADS,
                                    downloads.toString()
                                )
                                    .commit()

                                handleDownloadComplete(downloadId as Long, pkg as String)
                            }
                            Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
                        }
                    if (mDownloadList.isEmpty()) {
                        Log.d(TAG, "downloads done - now kill me and start install if needed")
                        if (isInstallPending()) {
                            triggerInstall()
                        }
                    }
                }
            }
        }
    }

    fun addDownload(id: Long, pkg: String) {
        Log.d(TAG, "ADD_DOWNLOAD DOWNLOAD_ID = " + id + " DOWNLOAD_PKG = " + pkg)

        if (id != -1L) {
            mPrefs.getString(Constants.PREF_CURRENT_DOWNLOADS, JSONObject().toString())?.let {
                val downloads = JSONObject(it)
                downloads.put(id.toString(), pkg)
                mPrefs.edit().putString(Constants.PREF_CURRENT_DOWNLOADS, downloads.toString())
                    .commit()

                Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
            }
            mDownloadList.add(id as Long)
        }
    }

    private fun handleDownloadComplete(downloadId: Long, pkg: String) {
        var uri: Uri? = mDownloadManager.getUriForDownloadedFile(downloadId)
        if (uri == null) {
            // includes also cancel
            return
        }
        mPrefs.getString(Constants.PREF_CURRENT_INSTALLS, JSONObject().toString())?.let {
            val installs = JSONObject(it)
            val data = JSONObject()
            data.put("pkg", pkg)
            data.put("uri", uri)
            installs.put(downloadId.toString(), data)
            mPrefs.edit().putString(Constants.PREF_CURRENT_INSTALLS, installs.toString())
                .commit()
            Log.d(TAG, "CURRENT_INSTALLS = " + installs)
        }
    }

    private fun triggerInstall() {
        val intent = Intent(Constants.ACTION_START_INSTALL)
        applicationContext.sendBroadcast(intent)
    }

    fun cancelDownloadApp(downloadId: Long) {
        mDownloadManager.remove(downloadId)
    }

    fun enqueDownloadApp(app: AppItem) {
        val url = app.fileUrl()
        val request: DownloadManager.Request =
            DownloadManager.Request(Uri.parse(url))
        val fileName = File(app.getFile()).name
        request.setDestinationInExternalFilesDir(
            applicationContext,
            null,
            fileName
        )

        val id = mDownloadManager.enqueue(request)
        app.mDownloadId = id
        addDownload(app.mDownloadId, app.packageName!!)
    }

    private fun isInstallPending(): Boolean {
        mPrefs.getString(Constants.PREF_CURRENT_INSTALLS, JSONObject().toString())?.let {
            val installs = JSONObject(it)
            return installs.length() != 0;
        }
        return false
    }
}