package org.omnirom.omnistore

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Path

interface OmniStoreApi {
    @GET("{app_list_file}.json")
    suspend fun getApps(@Path(value = "app_list_file") appListFile:String) : Response<List<AppItem>>

    @HEAD("{file_url}")
    suspend fun checkApp(@Path(value = "file_url") fileUrl:String) : Response<Void>
}