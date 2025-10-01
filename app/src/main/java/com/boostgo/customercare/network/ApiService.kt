package com.boostgo.customercare.network

import com.boostgo.customercare.model.SmsOrderRequest
import com.boostgo.customercare.model.SmsOrderResponse
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import com.boostgo.customercare.model.UpdateOrderSmsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {
    @GET("oam/get-new-order-sms")
    suspend fun getNewOrderSms(
        @Query("imei") imei: String,
        @Query("token") token: String
    ): Response<SmsOrderResponse>
    
    @POST("oam/update-order-sms")
    suspend fun updateOrderSms(@Body request: UpdateOrderSmsRequest): Response<UpdateOrderSmsResponse>
}
