/*
 *  Copyright (C) 2022 The OmniROM Project
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

import android.app.DownloadManager
import android.content.*
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.preference.PreferenceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CURRENT_DOWNLOADS
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(@ApplicationContext val applicationContext: Context) :
    ViewModel() {
    private val TAG = "OmniStore:MainViewModel"

    private val mDownloadManager: DownloadManager =
        applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val mPrefs: SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(applicationContext)

    private fun addDownload(id: Long, pkg: String) {
        Log.d(TAG, "ADD_DOWNLOAD DOWNLOAD_ID = " + id + " DOWNLOAD_PKG = " + pkg)

        if (id != -1L) {
            mPrefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())?.let {
                val downloads = JSONObject(it)
                downloads.put(id.toString(), pkg)
                mPrefs.edit().putString(PREF_CURRENT_DOWNLOADS, downloads.toString())
                    .apply()

                Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
            }
        }
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
        Log.d(TAG, "enqueDownloadApp url = " + url)

        val id = mDownloadManager.enqueue(request)
        app.mDownloadId = id
        addDownload(app.mDownloadId, app.packageName!!)
    }
}