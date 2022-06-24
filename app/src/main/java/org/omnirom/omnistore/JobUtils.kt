/*
 *  Copyright (C) 2020-2022 The OmniROM Project
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
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class JobUtils {
    private val TAG = "OmniStore:JobUtils"

    fun setupWorkManagerJob(context: Context) {
        Log.d(TAG, "setupWorkManagerJob")

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val work = PeriodicWorkRequestBuilder<CheckUpdatesWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(
                CheckUpdatesWorker::class.java.name,
                ExistingPeriodicWorkPolicy.REPLACE, work
            )
    }

    fun cancelWorkManagerJob(context: Context) {
        Log.d(TAG, "cancelWorkManagerJob")

        WorkManager.getInstance(context).cancelAllWork()
    }
}
