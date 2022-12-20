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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.net.ssl.HttpsURLConnection

object NetworkUtils {
    private val TAG = "OmniStore:NetworkUtils"

    interface NetworkTaskCallback {
        fun postAction(networkError: Boolean, reponseCode: Int)
    }

    class FetchAppsTask(
        context: Context,
        preAction: Runnable,
        postAction: NetworkTaskCallback,
        newAppsList: MutableList<AppItem>
    ) {
        private val mNewAppsList = newAppsList
        private val mPreaction: Runnable = preAction
        private val mPostAction: NetworkTaskCallback = postAction
        private val mContext: Context = context
        private var mNetworkError = false

        fun run() {
            mPreaction.run()

            val omniStoreApi =
                RetrofitManager.getInstance(mContext, RetrofitManager.baseUrl)
                    .create(OmniStoreApi::class.java)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appList = omniStoreApi.getApps("apps")
                    if (appList.isSuccessful) {
                        appList.body()?.let {
                            loadAppsList(it)
                            Log.d(TAG, "loadAppList size = " + mNewAppsList.size)

                            // add extra if available
                            val extraFiles = mutableListOf<String>()
                            extraFiles.add(
                                "apps-" + DeviceUtils().getProperty(
                                    mContext,
                                    "ro.build.version.release"
                                )
                            )
                            extraFiles.add(
                                "apps-" + DeviceUtils().getProperty(
                                    mContext,
                                    "ro.omni.device"
                                )
                            )
                            Log.d(TAG, "loadExtraAppList " + extraFiles)
                            loadExtraAppList(extraFiles, omniStoreApi)
                            Log.d(TAG, "loadExtraAppList size = " + mNewAppsList.size)
                        }
                    } else {
                        mNetworkError = true
                    }
                    val appsExtraList = omniStoreApi.getAppsExtra()
                    if (appsExtraList.isSuccessful) {
                        appsExtraList.body()?.let {
                            it.forEach { appsExtraItem ->
                                if (appsExtraItem.isValid()) {
                                    val extraBaseUrl = appsExtraItem.baseurl!!
                                    val extraFile = appsExtraItem.file!!
                                    val extraStoreApi =
                                        RetrofitManager.getInstance(mContext, extraBaseUrl)
                                            .create(OmniStoreApi::class.java)
                                    val extraAppsList = extraStoreApi.getApps(extraFile)
                                    if (extraAppsList.isSuccessful) {
                                        extraAppsList.body()?.let {
                                            Log.d(
                                                TAG,
                                                "loadExtraAppList from " + extraBaseUrl + " " + extraFile
                                            )
                                            loadAppsList(it)
                                            Log.d(TAG, "loadExtraAppList size = " + mNewAppsList.size)
                                        }
                                    } else {
                                        Log.d(TAG, "loadExtraAppList extraBaseUrl  = " + extraBaseUrl + " extraFile = " + " code = " + extraAppsList.code())
                                    }
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        mPostAction.postAction(
                            mNetworkError,
                            appList.code()
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

                if (appList.isSuccessful) {
                    if (appList.body() != null) {
                        Log.d(TAG, "loadExtraAppList " + file)
                        loadAppsList(appList.body()!!)
                    }
                } else {
                    Log.d(TAG, "loadExtraAppList file = " + file + " code = " + appList.code())
                }
            }
        }

        private fun loadAppsList(appsList: List<AppItem>) {
            for (app in appsList) {
                if (app.isValid() && app.isValidDevice(
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

    class CheckAppTask(
        context: Context,
        val appItem: AppItem,
        postAction: NetworkTaskCallback
    ) {
        private val mPostAction: NetworkTaskCallback = postAction
        private var responseCode: Int = HttpsURLConnection.HTTP_OK
        private val mContext = context

        fun run() {
            val omniStoreApi =
                RetrofitManager.getInstance(mContext, appItem.mBaseUrl)
                    .create(OmniStoreApi::class.java)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val reponse = omniStoreApi.checkApp(appItem.file!!)
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