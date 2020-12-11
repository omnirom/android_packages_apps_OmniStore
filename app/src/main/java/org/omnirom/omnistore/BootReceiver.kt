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