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

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.net.ssl.HttpsURLConnection

class NetworkUtils {
    private val TAG = "OmniStore:NetworkUtils"

    interface NetworkTaskCallback {
        fun postAction(networkError: Boolean, reponseCode: Int)
    }

    inner class FetchAppsTask(
        context: Context,
        preAction: Runnable,
        postAction: NetworkTaskCallback,
        newAppsList: ArrayList<AppItem>
    ) {
        private val mNewAppsList: ArrayList<AppItem> = newAppsList
        private val mPreaction: Runnable = preAction
        private val mPostAction: NetworkTaskCallback = postAction
        private val mContext: Context = context
        private var mNetworkError = false

        fun run() {
            mPreaction.run()

            val omniStoreApi =
                RetrofitManager.getInstance(mContext).create(OmniStoreApi::class.java)
            GlobalScope.launch {
                try {
                    val appList = omniStoreApi.getApps("apps")

                    if (appList.isSuccessful && appList.body() != null) {
                        loadAppsList(appList.body()!!)

                        // add extra if available
                        val extraFiles = mutableListOf<String>()
                        extraFiles.add(
                            DeviceUtils().getProperty(
                                mContext,
                                "ro.build.version.release"
                            )
                        )
                        extraFiles.add(DeviceUtils().getProperty(mContext, "ro.omni.device"))
                        loadExtraAppList(extraFiles, omniStoreApi)
                    } else {
                        mNetworkError = true
                    }
                    withContext(Dispatchers.Main) {
                        mPostAction.postAction(
                            mNetworkError,
                            HttpsURLConnection.HTTP_INTERNAL_ERROR
                        )
                    }
                } catch (e: RetrofitManager.NoConnectivityException) {
                    withContext(Dispatchers.Main) {
                        mPostAction.postAction(
                            true,
                            HttpsURLConnection.HTTP_INTERNAL_ERROR
                        )
                    }
                }
            }
        }

        private suspend fun loadExtraAppList(extraFiles: List<String>, omniStoreApi: OmniStoreApi) {
            extraFiles.forEach { file ->
                val appList = omniStoreApi.getApps(file)

                if (appList.isSuccessful && appList.body() != null) {
                    Log.d(TAG, "loadExtraAppList " + file)
                    loadAppsList(appList.body()!!)
                }
            }
        }

        private fun loadAppsList(appsList: List<AppItem>) {
            for (app in appsList) {
                if (app.isValidApp() && app.isValidDevice(
                        DeviceUtils().getProperty(
                            mContext,
                            "ro.omni.device"
                        )
                    )
                ) {
                    val idx = mNewAppsList.indexOf(app)
                    if (idx != -1)
                        mNewAppsList.removeAt(idx)
                    app.initState()
                    mNewAppsList.add(app)
                }
            }
        }
    }

    inner class CheckAppTask(
        context: Context,
        url: String,
        postAction: NetworkTaskCallback
    ) {
        private val mPostAction: NetworkTaskCallback = postAction
        private val mUrl: String = url
        private var responseCode: Int = HttpsURLConnection.HTTP_OK
        private val mContext = context

        fun run() {
            val omniStoreApi =
                RetrofitManager.getInstance(mContext).create(OmniStoreApi::class.java)
            GlobalScope.launch {
                try {
                    val reponse = omniStoreApi.checkApp(mUrl)
                    responseCode = reponse.code()

                    withContext(Dispatchers.Main) {
                        mPostAction.postAction(
                            responseCode != HttpsURLConnection.HTTP_OK,
                            responseCode
                        )
                    }
                } catch (e: RetrofitManager.NoConnectivityException) {
                    withContext(Dispatchers.Main) {
                        mPostAction.postAction(
                            true,
                            HttpsURLConnection.HTTP_INTERNAL_ERROR
                        )
                    }
                }
            }
        }
    }
}