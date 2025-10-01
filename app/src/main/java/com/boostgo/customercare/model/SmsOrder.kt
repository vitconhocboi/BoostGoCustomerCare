package com.boostgo.customercare.model

import com.google.gson.annotations.SerializedName

data class SmsOrder(
    @SerializedName("orderId")
    val orderId: String,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("number")
    val number: String,
    
    @SerializedName("address")
    val address: String,
    
    @SerializedName("order_time")
    val orderTime: String,
    
    @SerializedName("quantity")
    val quantity: Int,
    
    @SerializedName("cod")
    val cod: Int,
    
    @SerializedName("description")
    val description: String,
    
    @SerializedName("status")
    val status: Int
)

data class SmsOrderRequest(
    val imei: String,
    val token: String
)

data class SmsOrderResponse(
    val result: SmsOrder?
)

data class UpdateOrderSmsRequest(
    val token: String,
    val orderId: String,
    val status: String
)

data class UpdateOrderSmsResponse(
    val success: Boolean,
    val message: String?
)