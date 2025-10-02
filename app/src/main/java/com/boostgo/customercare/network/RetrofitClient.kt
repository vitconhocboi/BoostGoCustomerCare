package com.boostgo.customercare.network

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import android.util.Log


@Module
@InstallIn(SingletonComponent::class)
object RetrofitClient {
    private const val BASE_URL =
        "https://sales.bmctech.one/" // Replace with actual API base URL

    @Provides
    @Singleton
    fun provideApiService(): ApiService {
        // Create logging interceptor for debugging
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            when {
                message.startsWith("-->") -> Log.d("RetrofitClient", "🚀 REQUEST: $message")
                message.startsWith("<--") -> Log.d("RetrofitClient", "📥 RESPONSE: $message")
                message.startsWith("|") -> Log.d("RetrofitClient", "📄 BODY: $message")
                else -> Log.d("RetrofitClient", "📋 INFO: $message")
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val clientBuilder = OkHttpClient
            .Builder()
            .addInterceptor(loggingInterceptor)

        clientBuilder
            .connectTimeout(5, TimeUnit.MINUTES)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)

        Log.d("RetrofitClient", "Creating API service with base URL: $BASE_URL")
        val retrofit = Retrofit.Builder().baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()).client(clientBuilder.build())
            .build()
        return retrofit.create(ApiService::class.java)
    }
}
