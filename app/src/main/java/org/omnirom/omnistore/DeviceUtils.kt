package org.omnirom.omnistore

import android.content.Context
import android.util.Log
import java.lang.reflect.Method

class DeviceUtils {
    private val TAG = "OmniStore:DeviceUtils"

    fun getProperty(
        context: Context,
        key: String
    ): String {
        try {
            val systemProperties = context.classLoader.loadClass(
                "android.os.SystemProperties"
            )
            val get: Method = systemProperties.getMethod(
                "get", *arrayOf<Class<*>>(
                    String::class.java, String::class.java
                )
            )
            return get.invoke(null, key, "") as String
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "getProperty", e)
        }
        return ""
    }
}
