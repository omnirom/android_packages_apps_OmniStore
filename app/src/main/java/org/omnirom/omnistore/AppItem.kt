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
import com.google.gson.annotations.SerializedName

//     {
//        "title": "Calendar",
//        "file": "Calendar.apk",
//        "category": "core",
//        "package" : "com.android.calendar",
//        "icon" : "Calendar.png",
//        "versionCode" : "6",
//        "versionName" : "1.0.5",
//"devices": [
//"oneplus7t",
//],
//        "description" : "AOSP calendar application.",
//        "note" : "Depending on your setup you might also need Google Calendar Sync Adapter"
//    },
data class AppItem(
    val title: String,
    val file: String,
    val category: String,
    @SerializedName("package")
    val packageName: String,
    val icon: String,
    var versionCode: String,
    var versionName: String,
    @SerializedName("description")
    val _description: String,
    @SerializedName("note")
    val _note: String,
    val devices: List<String>,

    ) : ListItem {
    private val TAG = "OmniStore:AppItem"

    enum class InstallState { DISABLED, INSTALLED, UNINSTALLED }

    var mInstalled: InstallState = InstallState.UNINSTALLED
    var mDownloadId: Long = -1
    var mVersionCodeInstalled: Int = 0
    var mVersionNameInstalled: String = "unknown"

    fun isValied(device: String): Boolean {
        if (devices != null) {
            return devices.contains(device)
        }
        return true
    }

    fun fileUrl(context: Context): String {
        return Constants.getAppsRootUri(context) + file
    }

    fun iconUrl(context: Context): String {
        return Constants.getAppsRootUri(context) + icon
    }

    fun installEnabled(): Boolean {
        return appNotInstaled() || (appInstalled() && (versionCode.toInt() > mVersionCodeInstalled))
    }

    fun updateAvailable(): Boolean {
        return appInstalled() && (versionCode.toInt() > mVersionCodeInstalled)
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

    override fun title(): String {
        return title
    }

    override fun sortOrder(): Int {
        if (updateAvailable()) return 0
        return when (mInstalled) {
            InstallState.INSTALLED -> 1
            InstallState.UNINSTALLED -> 2
            InstallState.DISABLED -> 3
        }
    }

    fun note() : String {
        return _note ?: ""
    }

    fun description() : String {
        return _description ?: ""
    }

    fun updateAppStatus(packageManager: PackageManager) {
        try {
            val pkgInfo = packageManager.getPackageInfo(
                packageName,
                0
            )
            mVersionCodeInstalled = pkgInfo.versionCode
            mVersionNameInstalled = pkgInfo.versionName
            val enabled: Int =
                packageManager.getApplicationEnabledSetting(packageName)
            if (enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED ||
                enabled == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            ) {
                setInstalledStatus(InstallState.DISABLED)
            } else {
                setInstalledStatus(InstallState.INSTALLED)
            }
        } catch (e: Exception) {
            setInstalledStatus(InstallState.UNINSTALLED)
        }
    }

    fun initState() {
        mDownloadId = -1
        mInstalled = InstallState.UNINSTALLED
    }

    override fun equals(other: Any?): Boolean {
        if (other is AppItem)
            return packageName == other.packageName
        return false
    }
}
