package org.omnirom.omnistore

import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.omnirom.omnistore.Constants.APPS_BASE_URI

class AppItem(val appData: JSONObject) {
    private var mInstalled: InstallState = InstallState.UNINSTALLED
    var mDownloadId: Long = -1
    var mVersionCode: Int = -1
    var mVersionName: String = ""
    private val TAG = "OmniStore:AppItem"

    enum class InstallState { DISABLED, INSTALLED, UNINSTALLED}

    fun isValied(device: String): Boolean {
        try {
            appData.get("file").toString()
            appData.get("title").toString()
            appData.get("package").toString()
            appData.get("icon").toString()
            appData.get("versionCode").toString()
            appData.get("versionName").toString()

            if (appData.has("devices")) {
                if (device.isNotEmpty()) {
                    val devices: JSONArray = appData.getJSONArray("devices")
                    for (i in 0 until devices.length()) {
                        if (devices.get(i).toString() == device) {
                            return true;
                        }
                    }
                }
            } else {
                return true
            }
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

    fun title(): String {
        try {
            return appData.get("title").toString()
        } catch (e: JSONException) {
            return "unknown"
        }
    }

    fun iconUrl(): String? {
        try {
            return APPS_BASE_URI + appData.get("icon").toString()
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
        return appNotInstaleed() || (appInstalled() && (versionCode() > mVersionCode))
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

    fun appNotInstaleed(): Boolean {
        return mInstalled == InstallState.UNINSTALLED
    }

    fun setInstalledStatus(status: InstallState) {
        mInstalled = status
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