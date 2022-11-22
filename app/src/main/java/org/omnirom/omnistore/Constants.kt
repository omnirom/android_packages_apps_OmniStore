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
    const val PREF_CHECK_UPDATES_OLD = "check_updates"
    const val PREF_CURRENT_APPS = "current_apps"
    const val PREF_CURRENT_INSTALLS = "current_installs"
    const val PREF_CHECK_UPDATES_WORKER = "check_updates_worker"
    const val PREF_UPDATE_APPS = "update_apps"
    const val PREF_POST_NOTIFICATION = "post_notifications"

    //const val ACTION_ADD_DOWNLOAD = "add_download"
    //const val ACTION_CANCEL_DOWNLOAD = "cancel_download"
    const val ACTION_START_INSTALL = "start_install"
    const val EXTRA_DOWNLOAD_ID = "id"
    const val EXTRA_DOWNLOAD_PKG = "pkg"

    const val NOTIFICATION_CHANNEL_UPDATE = "org.omnirom.omnistore.notification.updates"
    const val NOTIFICATION_CHANNEL_PROGRESS = "org.omnirom.omnistore.notification.progress"

    const val TYPE_APP_ITEM = 0
    const val TYPE_SEPARATOR_ITEM = 1
}
