package org.omnirom.omnistore

import android.content.Context
import android.net.ConnectivityManager
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException


object RetrofitManager {
    const val baseUrl = "https://dl.omnirom.org/store/"
    const val baseImageUrl = baseUrl + "images/"

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

    private fun createGsonConverter(baseUrl: String): Converter.Factory {
        val gsonBuilder = GsonBuilder()
        gsonBuilder.registerTypeAdapter(AppItem::class.java, AppItemDeserializer(baseUrl))
        val gson = gsonBuilder.create()
        return GsonConverterFactory.create(gson)
    }

    fun getInstance(context: Context, baseUrl: String): Retrofit {
        val oktHttpClient = OkHttpClient.Builder()
            .addInterceptor(NetworkConnectionInterceptor(context))

        return Retrofit.Builder().baseUrl(baseUrl)
            .addConverterFactory(createGsonConverter(baseUrl))
            .client(oktHttpClient.build())
            .build()
    }
}