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
    val title: String?,
    val file: String?,
    val category: String?,
    @SerializedName("package")
    val packageName: String?,
    val icon: String?,
    val versionCode: String?,
    val versionName: String?,
    val description: String?,
    val note: String?,
    val devices: List<String>?,

    ) : ListItem {
    private val TAG = "OmniStore:AppItem"

    enum class InstallState { DISABLED, INSTALLED, UNINSTALLED }

    var mInstalled: InstallState = InstallState.UNINSTALLED
    var mDownloadId: Long = -1
    var versionCodeInstalled: Int = 0
    var versionNameInstalled: String = ""

    fun isValidApp(): Boolean {
        return packageName != null && file != null && icon != null && title != null && versionCode != null && versionName != null
    }

    fun isValidDevice(device: String): Boolean {
        if (devices != null) {
            return devices.contains(device)
        }
        return true
    }

    fun fileUrl(): String {
        return RetrofitManager.baseUrl + file
    }

    fun iconUrl(): String {
        return RetrofitManager.baseUrl + icon
    }

    fun installEnabled(): Boolean {
        if (versionCode == null) {
            return false
        }
        return appNotInstaled() || (appInstalled() && (versionCode.toInt() > versionCodeInstalled))
    }

    fun updateAvailable(): Boolean {
        if (versionCode == null) {
            return false
        }
        return appInstalled() && (versionCode.toInt() > versionCodeInstalled)
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
        return title!!
    }

    override fun sortOrder(): Int {
        if (updateAvailable()) return 0
        return when (mInstalled) {
            InstallState.INSTALLED -> 1
            InstallState.UNINSTALLED -> 2
            InstallState.DISABLED -> 3
        }
    }

    fun note(): String {
        return note ?: ""
    }

    fun description(): String {
        return description ?: ""
    }

    fun updateAppStatus(packageManager: PackageManager) {
        if (packageName == null) {
            return
        }
        try {
            val pkgInfo = packageManager.getPackageInfo(
                packageName,
                0
            )
            @Suppress("DEPRECATION")
            versionCodeInstalled = pkgInfo.versionCode
            versionNameInstalled = pkgInfo.versionName
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

    override fun hashCode(): Int {
        return packageName.hashCode()
    }

    fun versionNameCurrent() : String {
        if (updateAvailable() || appNotInstaled()) {
            return versionName?:""
        } else if (appInstalled() || appDisabled()) {
            return versionNameInstalled
        }
        return ""
    }

    @JvmName("getFile1")
    fun getFile() : String {
        return file!!
    }

    @JvmName("getPackageName1")
    fun getPackageName() : String {
        return packageName!!
    }
}
