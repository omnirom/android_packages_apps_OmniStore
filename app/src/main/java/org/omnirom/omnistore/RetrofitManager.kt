package org.omnirom.omnistore

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException


object RetrofitManager {
    val baseUrl = "https://dl.omnirom.org/"

    class NoConnectivityException : IOException() {
    }

    class NetworkConnectionInterceptor(context: Context) : Interceptor {
        private val mContext: Context

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            if (!isConnected) {
                throw NoConnectivityException()
                // Throwing our custom exception 'NoConnectivityException'
            }
            val builder: Request.Builder = chain.request().newBuilder()
            return chain.proceed(builder.build())
        }

        val isConnected: Boolean
            get() {
                val connectivityManager =
                    mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val netInfo = connectivityManager.activeNetworkInfo
                return netInfo != null && netInfo.isConnected
            }

        init {
            mContext = context
        }
    }

    fun getInstance(context: Context): Retrofit {
        val oktHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkConnectionInterceptor(context))

        return Retrofit.Builder().baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(oktHttpClient.build())
            .build()
    }
}