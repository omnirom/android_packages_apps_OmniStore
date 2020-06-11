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
        //builder.setPeriodic(TimeUnit.DAYS.toMillis(1), TimeUnit.HOURS.toMillis(2))
        builder.setPeriodic(TimeUnit.HOURS.toMillis(12), TimeUnit.MINUTES.toMillis(30))
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