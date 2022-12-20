package org.omnirom.omnistore

import androidx.annotation.Keep

@Keep
data class AppsExtraItem(val baseurl: String?,
                         val file: String?) {
    fun isValid(): Boolean {
        return !baseurl.isNullOrEmpty() && !file.isNullOrEmpty()
    }
}
