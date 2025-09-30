package com.boostgo.customercare.network

import com.boostgo.customercare.model.SmsOrderRequest
import com.boostgo.customercare.model.SmsOrderResponse
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import com.boostgo.customercare.model.UpdateOrderSmsResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("oam/get-new-order-sms")
    suspend fun getNewOrderSms(@Body request: SmsOrderRequest): Response<SmsOrderResponse>
    
    @POST("oam/update-order-sms")
    suspend fun updateOrderSms(@Body request: UpdateOrderSmsRequest): Response<UpdateOrderSmsResponse>
}
