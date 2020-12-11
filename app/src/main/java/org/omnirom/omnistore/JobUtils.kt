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

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.util.Log
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit

class JobUtils {
    private val TAG = "OmniStore:JobUtils"

    fun scheduleCheckUpdates(context: Context) {
        cancelCheckForUpdates(context)

        Log.d(TAG, "scheduleCheckUpdates")
        val serviceComponent = ComponentName(context, CheckUpdatesService::class.java)
        val builder = JobInfo.Builder(0, serviceComponent)
        builder.setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(2))
        //builder.setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(15))
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        jobScheduler.schedule(builder.build())
    }

    fun cancelCheckForUpdates(context: Context) {
        Log.d(TAG, "cancelCheckForUpdates")
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        jobScheduler.cancelAll()
    }
}
