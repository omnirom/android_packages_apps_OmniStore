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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.json.JSONObject
import org.omnirom.omnistore.MainActivity.Companion.ACTION_START_INSTALL
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CURRENT_DOWNLOADS
import org.omnirom.omnistore.SettingsActivity.Companion.PREF_CURRENT_INSTALLS

class DownloadReceiver : BroadcastReceiver() {
    private val TAG = "OmniStore:DownloadReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals(DownloadManager.ACTION_DOWNLOAD_COMPLETE)) {
            val prefs: SharedPreferences =
                PreferenceManager.getDefaultSharedPreferences(context)
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId != -1L) {
                prefs.getString(PREF_CURRENT_DOWNLOADS, JSONObject().toString())
                    ?.let {
                        val downloads = JSONObject(it)
                        Log.d(
                            TAG,
                            "ACTION_DOWNLOAD_COMPLETE DOWNLOAD_ID = " + downloadId + " " + downloads
                        )
                        if (downloads.has(downloadId.toString())) {
                            val pkg = downloads.get(downloadId.toString())
                            downloads.remove(downloadId.toString())
                            prefs.edit().putString(
                                PREF_CURRENT_DOWNLOADS,
                                downloads.toString()
                            ).apply()

                            handleDownloadComplete(
                                context,
                                prefs,
                                downloadId,
                                pkg as String
                            )
                            if (downloads.length() == 0) {
                                Log.d(
                                    TAG,
                                    "downloads done - now kill me and start install if needed"
                                )
                                if (isInstallPending(prefs)) {
                                    triggerInstall(context)
                                }
                            }
                        }
                        Log.d(TAG, "CURRENT_DOWNLOADS = " + downloads)
                    }

            }
        }
    }

    private fun handleDownloadComplete(
        context: Context,
        prefs: SharedPreferences,
        downloadId: Long,
        pkg: String
    ) {
        val downloadManager: DownloadManager =
            context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val uri = downloadManager.getUriForDownloadedFile(downloadId)
            ?: return
        prefs.getString(PREF_CURRENT_INSTALLS, JSONObject().toString())?.let {
            val installs = JSONObject(it)
            val data = JSONObject()
            data.put("pkg", pkg)
            data.put("uri", uri)
            installs.put(downloadId.toString(), data)
            prefs.edit().putString(PREF_CURRENT_INSTALLS, installs.toString())
                .apply()
            Log.d(TAG, "CURRENT_INSTALLS = " + installs)
        }
    }

    private fun triggerInstall(context: Context) {
        val intent = Intent(ACTION_START_INSTALL)
        context.sendBroadcast(intent)
    }


    private fun isInstallPending(prefs: SharedPreferences): Boolean {
        prefs.getString(PREF_CURRENT_INSTALLS, JSONObject().toString())?.let {
            val installs = JSONObject(it)
            return installs.length() != 0
        }
        return false
    }
}