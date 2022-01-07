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
import android.os.AsyncTask
import android.util.Log
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

class NetworkUtils {
    private val TAG = "OmniStore:NetworkUtils"
    private val HTTP_READ_TIMEOUT = 30000
    private val HTTP_CONNECTION_TIMEOUT = 30000
    var mNetworkError = false

    interface NetworkTaskCallback {
        fun postAction(networkError: Boolean, reponseCode: Int)
    }

    inner class FetchAppsTask(
        context: Context,
        preAction: Runnable,
        postAction: NetworkTaskCallback,
        newAppsList: ArrayList<AppItem>
    ) : AsyncTask<String, Int, Int>() {
        val mNewAppsList: ArrayList<AppItem> = newAppsList
        val mPreaction: Runnable = preAction
        val mPostAction: NetworkTaskCallback = postAction
        val mContext: Context = context

        override fun onPreExecute() {
            super.onPreExecute()
            mPreaction.run()
            mNetworkError = false
        }

        override fun doInBackground(vararg params: String?): Int {
            var appListData: String? =
                downloadUrlMemoryAsString(Constants.getAppsQueryUri(mContext, ""))
            if (appListData != null) {
                loadAppsList(appListData)

                // add extra if available
                appListData = downloadUrlMemoryAsString(
                    Constants.getAppsQueryUri(
                        mContext,
                        DeviceUtils().getProperty(mContext, "ro.build.version.release")
                    )
                )
                if (appListData != null) {
                    loadAppsList(appListData)
                }
            } else {
                mNetworkError = true
            }
            return 0
        }

        private fun loadAppsList(appListData: String) {
            val apps = JSONArray(appListData)
            //Log.d(TAG, "" + apps)
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i);
                val appData = AppItem(app)
                if (appData.isValied(DeviceUtils().getProperty(mContext, "ro.omni.device"))) {
                    val idx = mNewAppsList.indexOf(appData)
                    if (idx != -1)
                        mNewAppsList.removeAt(idx)
                    mNewAppsList.add(appData)
                }
            }
        }

        override fun onPostExecute(result: Int?) {
            super.onPostExecute(result)
            mPostAction.postAction(mNetworkError, HttpsURLConnection.HTTP_INTERNAL_ERROR)
        }
    }

    inner class CheckAppTask(
        url: String,
        postAction: NetworkTaskCallback
    ) : AsyncTask<String, Int, Int>() {
        val mPostAction: NetworkTaskCallback = postAction
        val mUrl: String = url
        var responseCode: Int = HttpsURLConnection.HTTP_OK

        override fun doInBackground(vararg params: String?): Int {
            try {
                responseCode = checkHttpsUrl(mUrl)
            } catch (e: Exception) {
                responseCode = HttpsURLConnection.HTTP_INTERNAL_ERROR
            }
            return 0
        }

        override fun onPostExecute(result: Int?) {
            super.onPostExecute(result)
            mPostAction.postAction(responseCode != HttpsURLConnection.HTTP_OK, responseCode)
        }
    }

    fun setupHttpsRequest(urlStr: String): HttpsURLConnection? {
        Log.d(TAG, "setupHttpsRequest: " + urlStr)
        val url = URL(urlStr)
        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT)
        urlConnection.setReadTimeout(HTTP_READ_TIMEOUT)
        urlConnection.setRequestMethod("GET")
        urlConnection.setDoInput(true)
        urlConnection.setDefaultUseCaches(false)
        urlConnection.connect()
        val code: Int = urlConnection.getResponseCode()
        if (code != HttpsURLConnection.HTTP_OK) {
            Log.d(TAG, "response: " + code)
            return null
        }
        return urlConnection
    }

    fun checkHttpsUrl(urlStr: String): Int {
        Log.d(TAG, "checkHttpsUrl: " + urlStr)
        val url = URL(urlStr)
        val urlConnection = url.openConnection() as HttpsURLConnection
        urlConnection.setConnectTimeout(HTTP_CONNECTION_TIMEOUT)
        urlConnection.setReadTimeout(HTTP_READ_TIMEOUT)
        urlConnection.setRequestMethod("GET")
        urlConnection.setDoInput(true)
        urlConnection.setDefaultUseCaches(false)
        urlConnection.connect()
        val code = urlConnection.getResponseCode()
        urlConnection.disconnect()
        return code
    }


    private fun downloadUrlMemoryAsString(url: String): String? {
        Log.d(TAG, "downloadUrlMemoryAsString: " + url)
        var urlConnection: HttpsURLConnection? = null
        return try {
            urlConnection = setupHttpsRequest(url)
            if (urlConnection == null) {
                return null
            }
            val input: InputStream = urlConnection.inputStream
            val byteArray = ByteArrayOutputStream()
            var byteInt: Int = 0
            while (input.read().also({ byteInt = it }) >= 0) {
                byteArray.write(byteInt)
            }
            val bytes: ByteArray = byteArray.toByteArray() ?: return null
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Download failed for any number of reasons, timeouts, connection
            // drops, etc. Just log it in debugging mode.
            Log.e(TAG, "downloadUrlMemoryAsString " + e, e)
            null
        } finally {
            urlConnection?.disconnect()
        }
    }
}