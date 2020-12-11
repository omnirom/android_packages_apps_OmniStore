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

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class AppItem(val appData: JSONObject) : ListItem {
    private var mInstalled: InstallState = InstallState.UNINSTALLED
    var mDownloadId: Long = -1
    var mVersionCode: Int = -1
    var mVersionName: String = ""
    private val TAG = "OmniStore:AppItem"

    enum class InstallState { DISABLED, INSTALLED, UNINSTALLED }

    fun isValied(device: String): Boolean {
        try {
            appData.get("file").toString()
            appData.get("title").toString()
            appData.get("package").toString()
            appData.get("icon").toString()
            appData.get("versionCode").toString()
            appData.get("versionName").toString()

            var matchFilter = false
            if (appData.has("devices")) {
                if (device.isNotEmpty()) {
                    val devices: JSONArray = appData.getJSONArray("devices")
                    for (i in 0 until devices.length()) {
                        if (devices.get(i).toString() == device) {
                            matchFilter = true;
                        }
                    }
                }
            } else {
                matchFilter = true
            }
            return matchFilter
            /*if (matchFilter) {
                // TODO do we really need to check?
                if (NetworkUtils().setupHttpsRequest(fileUrl()!!) != null) {
                    return true
                }
            }*/
        } catch (e: JSONException) {
            Log.e(TAG, "isValied", e)
        }
        return false
    }

    fun file(): String {
        try {
            return appData.get("file").toString()
        } catch (e: JSONException) {
            return "unknown"
        }
    }

    fun fileUrl(context: Context): String? {
        try {
            return Constants.getAppsRootUri(context) + appData.get("file").toString()
        } catch (e: JSONException) {
            return null
        }
    }

    override fun title(): String {
        try {
            return appData.get("title").toString()
        } catch (e: JSONException) {
            return "unknown"
        }
    }

    fun iconUrl(context: Context): String? {
        try {
            return Constants.getAppsRootUri(context) + appData.get("icon").toString()
        } catch (e: JSONException) {
            return null
        }
    }

    fun pkg(): String {
        try {
            return appData.get("package").toString()
        } catch (e: JSONException) {
            return "unknown"
        }
    }

    fun note(): String? {
        if (appData.has("note")) {
            try {
                return appData.get("note").toString()
            } catch (e: JSONException) {
            }
        }
        return null
    }

    fun description(): String? {
        if (appData.has("description")) {
            try {
                return appData.get("description").toString()
            } catch (e: JSONException) {
            }
        }
        return null
    }

    fun versionName(): String {
        try {
            return appData.get("versionName").toString()
        } catch (e: JSONException) {
            return "unknown"
        }
    }

    override fun toString(): String {
        return appData.toString()
    }

    fun versionCode(): Int {
        try {
            return appData.get("versionCode").toString().toInt()
        } catch (e: JSONException) {
            return 0
        }
    }

    fun installEnabled(): Boolean {
        return appNotInstaled() || (appInstalled() && (versionCode() > mVersionCode))
    }

    fun updateAvailable(): Boolean {
        return appInstalled() && (versionCode() > mVersionCode)
    }

    fun appSettingsEnabled(): Boolean {
        return mInstalled != InstallState.UNINSTALLED
    }

    fun appDisabled(): Boolean {
        return mInstalled == InstallState.DISABLED
    }

    fun appInstalled(): Boolean {
        return mInstalled == InstallState.INSTALLED
    }

    fun appNotInstaled(): Boolean {
        return mInstalled == InstallState.UNINSTALLED
    }

    fun setInstalledStatus(status: InstallState) {
        mInstalled = status
    }

    override fun sortOrder(): Int {
        if (updateAvailable()) return 0
        when (mInstalled) {
            InstallState.INSTALLED -> return 1
            InstallState.UNINSTALLED -> return 2
            InstallState.DISABLED -> return 3
        }
    }

    fun updateAppStatus(packageManager: PackageManager) {
        try {
            val pkgInfo = packageManager.getPackageInfo(
                pkg(),
                0
            )
            mVersionCode = pkgInfo.versionCode
            mVersionName = pkgInfo.versionName
            val enabled: Int =
                packageManager.getApplicationEnabledSetting(pkg())
            if (enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            ) {
                setInstalledStatus(AppItem.InstallState.DISABLED)
            } else {
                setInstalledStatus(AppItem.InstallState.INSTALLED)
            }
        } catch (e: Exception) {
            setInstalledStatus(AppItem.InstallState.UNINSTALLED)
            mVersionCode = -1
            mVersionName = "unknown"
        }
    }
}