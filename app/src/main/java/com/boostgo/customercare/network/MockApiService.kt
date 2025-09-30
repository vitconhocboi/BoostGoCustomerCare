package com.boostgo.customercare.network

import com.boostgo.customercare.model.SmsOrder
import com.boostgo.customercare.model.SmsOrderRequest
import com.boostgo.customercare.model.SmsOrderResponse
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import com.boostgo.customercare.model.UpdateOrderSmsResponse
import kotlinx.coroutines.delay
import retrofit2.Response
import java.text.SimpleDateFormat
import java.util.*

/**
 * Mock API Service for testing when the real API is not available
 */
class MockApiService : ApiService {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val random = Random()
    
    // Sample mock data
    private val mockOrders = listOf(
        SmsOrder(
            orderId = "ORD-001",
            name = "John Doe",
            number = "0349542680",
            address = "123 Main St, New York, NY 10001",
            orderTime = dateFormat.format(Date()),
            description = "Pizza Margherita - Large"
        )
    )
    
    override suspend fun getNewOrderSms(request: SmsOrderRequest): Response<SmsOrderResponse> {
        // Simulate network delay
        delay(5000) // 1-3 seconds delay
        
        // Simulate occasional failures (10% chance)
        if (random.nextFloat() < 0.1f) {
            return Response.error(500, okhttp3.ResponseBody.create(null, "Mock server error"))
        }
        
        // Return random number of orders (0-3 orders)
        val numberOfOrders = random.nextInt(4)
        val selectedOrders = mockOrders.shuffled().take(numberOfOrders)
        
        val response = SmsOrderResponse(
            success = true,
            data = selectedOrders,
            message = if (selectedOrders.isEmpty()) "No new orders" else "Found ${selectedOrders.size} new orders"
        )
        
        return Response.success(response)
    }
    
    override suspend fun updateOrderSms(request: UpdateOrderSmsRequest): Response<UpdateOrderSmsResponse> {
        // Simulate network delay
        delay(1000) // 1 second delay
        
        // Simulate occasional failures (5% chance)
        if (random.nextFloat() < 0.05f) {
            return Response.error(500, okhttp3.ResponseBody.create(null, "Mock server error"))
        }
        
        val response = UpdateOrderSmsResponse(
            success = true,
            message = "Order ${request.orderId} updated to ${request.status}"
        )
        
        return Response.success(response)
    }
    
    /**
     * Get all mock orders for testing
     */
    fun getAllMockOrders(): List<SmsOrder> = mockOrders
    
    /**
     * Add a new mock order
     */
    fun addMockOrder(order: SmsOrder) {
        // In a real implementation, you might want to store this in a list
        // For now, this is just for demonstration
    }
}
