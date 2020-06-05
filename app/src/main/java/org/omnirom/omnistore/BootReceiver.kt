package org.omnirom.omnistore

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    private val TAG = "OmniStore:NetworkUtils"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            if (context != null) {
                Log.d(TAG, "onReceive " + intent?.action)
                DeviceUtils().setAlarm(context)
            }
        }
    }
}