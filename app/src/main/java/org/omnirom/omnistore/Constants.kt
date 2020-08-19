package org.omnirom.omnistore

import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.webkit.URLUtil

object Constants {
    const val PREF_CURRENT_DOWNLOADS = "current_downloads"
    const val PREF_CHECK_UPDATES = "check_updates"
    const val PREF_VIEW_GROUPS = "view_groups"
    const val PREF_CURRENT_APPS = "current_apps"
    const val ACTION_ADD_DOWNLOAD = "add_download"
    const val ACTION_CANCEL_DOWNLOAD = "cancel_download"
    const val EXTRA_DOWNLOAD_ID = "id"
    const val EXTRA_DOWNLOAD_PKG = "pkg"

    const val NOTIFICATION_CHANNEL_UPDATE = "org.omnirom.omnistore.notification.updates"
    const val NOTIFICATION_CHANNEL_PROGRESS = "org.omnirom.omnistore.notification.progress"

    const val TYPE_APP_ITEM = 0
    const val TYPE_SEPARATOR_ITEM = 1

    private fun getAppsBaseUrl(context: Context): String {
        var s: String? = Settings.System.getString(context.contentResolver, "store_base_url")
            ?: return "https://dl.omnirom.org/"
        return s!!
    }

    fun getAppsRootUri(context: Context): String {
        var rootUri: String? = Settings.System.getString(context.contentResolver, "store_root_uri")
            ?: "store/"
        if (URLUtil.isNetworkUrl(rootUri)) {
            return rootUri!!;
        }
        val base: Uri = Uri.parse(getAppsBaseUrl(context))
        val u: Uri = Uri.withAppendedPath(base, rootUri)
        return u.toString()
    }

    fun getAppsQueryUri(context: Context): String {
        var queryUri: String? = Settings.System.getString(context.contentResolver, "store_query_uri")
            ?: "store/apps.json"
        if (URLUtil.isNetworkUrl(queryUri)) {
            return queryUri!!;
        }
        val base: Uri = Uri.parse(getAppsBaseUrl(context))
        val u: Uri = Uri.withAppendedPath(base, queryUri)
        return u.toString()
    }
}
