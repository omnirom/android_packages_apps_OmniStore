package org.omnirom.omnistore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import org.omnirom.omnistore.Constants.PREF_CHECK_UPDATES

class BootReceiver : BroadcastReceiver() {
    private val TAG = "OmniStore:BootReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (context != null) {
                val prefs: SharedPreferences =
                    PreferenceManager.getDefaultSharedPreferences(context)
                if (prefs.getBoolean(PREF_CHECK_UPDATES, false)) {
                    Log.d(TAG, "onReceive " + intent?.action)
                    JobUtils().scheduleCheckUpdates(context)
                }
            }
        }
    }
}