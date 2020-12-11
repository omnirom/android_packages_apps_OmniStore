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
