package org.omnirom.omnistore

object Constants {
    const val APPS_BASE_URI = "https://dl.omnirom.org/store/"
    const val APPS_LIST_URI = APPS_BASE_URI + "apps.json"
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
}
