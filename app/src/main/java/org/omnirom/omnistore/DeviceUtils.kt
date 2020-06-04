package org.omnirom.omnistore

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.omnirom.omnistore.Constants.ACTION_CHECK_UPDATES
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.*

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

    fun cancelAlarm(context: Context) {
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val intent = Intent(context, DownloadService::class.java)
        intent.action = ACTION_CHECK_UPDATES
        var alarmIntent: PendingIntent = intent.let { intent ->
            PendingIntent.getService(
                context, 0, intent,
                0
            )
        }
        Log.d(TAG, "cancelAlarm")
        alarmManager?.cancel(alarmIntent)

        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(Constants.PREF_ALARM_ACTIVE, false)
            .commit()
    }

    fun setAlarm(context: Context) {
        val sdf = SimpleDateFormat("yyyy.MM.dd HH:mm:ss")
        val alarmManager =
            context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

        val intent = Intent(context, DownloadService::class.java)
        intent.action = ACTION_CHECK_UPDATES
        var alarmIntent: PendingIntent = intent.let { intent ->
            PendingIntent.getService(
                context, 0, intent,
                0
            )
        }

        alarmManager?.cancel(alarmIntent)

        val calendar: Calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
        }

        Log.d(
            TAG,
            "setAlarm " + sdf.format(Date(calendar.timeInMillis + 60 * 60 * 1000))
        )
        alarmManager?.setInexactRepeating(
            AlarmManager.RTC,
            calendar.timeInMillis,
            60 * 60 * 1000,
            alarmIntent
        )
        val prefs: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean(Constants.PREF_ALARM_ACTIVE, true)
            .commit()
    }
}
