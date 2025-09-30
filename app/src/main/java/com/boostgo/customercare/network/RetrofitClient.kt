package com.boostgo.customercare.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val BASE_URL = "https://your-api-base-url.com/" // Replace with actual API base URL
    
    // Toggle this to use mock data when API is not live
    private const val USE_MOCK_API = true // Set to false when your API is live
    
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
    
    private val mockApiService = MockApiService()
    
    val apiService: ApiService by lazy {
        if (USE_MOCK_API) {
            mockApiService
        } else {
            retrofit.create(ApiService::class.java)
        }
    }
    
    /**
     * Check if currently using mock API
     */
    fun isUsingMockApi(): Boolean = USE_MOCK_API
    
    /**
     * Get mock service for testing purposes
     */
    fun getMockService(): MockApiService = mockApiService
}
