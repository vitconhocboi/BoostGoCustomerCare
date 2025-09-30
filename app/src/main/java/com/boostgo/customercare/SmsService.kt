package com.boostgo.customercare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.boostgo.customercare.database.SmsDatabase
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.model.SmsOrderRequest
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import com.boostgo.customercare.network.RetrofitClient
import com.boostgo.customercare.utils.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SmsService : Service() {

    companion object {
        const val CHANNEL_ID = "SMS_SERVICE_CHANNEL"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_POLLING = "START_POLLING"
        const val ACTION_STOP_POLLING = "STOP_POLLING"
        const val EXTRA_IMEI = "imei"
        const val TOKEN = "p8cdEszEHaoujFrZsFh405z7oAtHbA1g"
    }

    private lateinit var notificationManager: NotificationManager
    private var isPolling = false
    private var pollingJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_POLLING -> {
                val imei = getDeviceImei()
                if (imei != null) {
                    startPolling(imei, TOKEN)
                }
            }

            ACTION_STOP_POLLING -> {
                stopPolling()
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SMS Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "SMS sending service"
            }

            val notificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notificationIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Service")
            .setContentText("Polling for new SMS orders...")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getDeviceImei(): String? {
        return Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    private fun startPolling(imei: String, token: String) {
        if (isPolling) return

        isPolling = true
        startForegroundService()

        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isPolling) {
                try {
                    fetchAndSendSms(imei, token)
                } catch (e: Exception) {
                    Log.e("SmsService", "Error in polling: ${e.message}")
                }

                delay(5000) // Wait 5 seconds
            }
        }
    }

    private fun stopPolling() {
        isPolling = false
        pollingJob?.cancel()
        stopForeground(true)
        stopSelf()
    }

    private suspend fun fetchAndSendSms(imei: String, token: String) {
        try {
            // Log if using mock API
            if (RetrofitClient.isUsingMockApi()) {
                Log.i("SmsService", "Using MOCK API - API is not live yet")
            }
            
            val request = SmsOrderRequest(imei = imei, token = token)
            val response = RetrofitClient.apiService.getNewOrderSms(request)

            if (response.isSuccessful && response.body()?.success == true) {
                val orders = response.body()?.data
                if (!orders.isNullOrEmpty()) {
                    Log.d("SmsService", "Fetched ${orders.size} new orders")

                    for (order in orders) {
                        val message = createSmsMessage(order)
                        sendSms(order.number, message, order.orderId)
                    }
                } else {
                    Log.d("SmsService", "No new orders found")
                }
            } else {
                Log.e("SmsService", "API call failed: ${response.message()}")
            }
        } catch (e: Exception) {
            Log.e("SmsService", "Error fetching orders: ${e.message}")
        }
    }

    private fun createSmsMessage(order: com.boostgo.customercare.model.SmsOrder): String {
        return """
            Customer: ${order.name}
            Address: ${order.address}
            Order Time: ${order.orderTime}
            Description: ${order.description}
        """.trimIndent()
    }

    private fun sendSms(phoneNumber: String, message: String, orderId: String) {
        try {
            // Check SMS permission before sending
            if (!PermissionHelper.hasSmsPermission(this)) {
                Log.e("SmsService", "SMS permission not granted")
                storeMessage(phoneNumber, message, "Failed - No SMS permission")
                return
            }
            
            startForegroundService()

            val smsManager = SmsManager.getDefault()

            // Create PendingIntents for status tracking
            val sentIntent = Intent("SMS_SENT")
            val sentPI = PendingIntent.getBroadcast(
                this, 0, sentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deliveredIntent = Intent("SMS_DELIVERED")
            val deliveredPI = PendingIntent.getBroadcast(
                this, 0, deliveredIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Send SMS
            if (message.length <= 160) {
                smsManager.sendTextMessage(phoneNumber, null, message, sentPI, deliveredPI)
            } else {
                Log.i(
                    "SmsService",
                    "Fail to sent SMS to $phoneNumber due to message length exceeds 160 characters"
                )
                storeMessage(phoneNumber, message, "Failed")
                return
            }

            // Store message in database
            storeMessage(phoneNumber, message, "Sending")

            // Update order status to "sent"
            updateOrderStatus(orderId, "sent")

            Log.i("SmsService", "SMS sent to $phoneNumber")

        } catch (e: Exception) {
            Log.e("SmsService", "Error sending SMS: ${e.message}")
            storeMessage(phoneNumber, message, "Failed")
        }
    }

    private fun storeMessage(phoneNumber: String, message: String, status: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = SmsDatabase.getDatabase(this@SmsService)
                val smsMessage = SmsMessage(
                    phoneNumber = phoneNumber,
                    message = message,
                    status = status
                )
                database.smsMessageDao().insertMessage(smsMessage)
                Log.d("SmsService", "Message stored: $phoneNumber - $status")
            } catch (e: Exception) {
                Log.e("SmsService", "Error storing message: ${e.message}")
            }
        }
    }
    
    private fun updateOrderStatus(orderId: String, status: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = UpdateOrderSmsRequest(
                    token = TOKEN,
                    orderId = orderId,
                    status = status
                )
                
                val response = RetrofitClient.apiService.updateOrderSms(request)
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Log.d("SmsService", "Order $orderId updated to $status: ${response.body()?.message}")
                } else {
                    Log.e("SmsService", "Failed to update order $orderId: ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("SmsService", "Error updating order status: ${e.message}")
            }
        }
    }
}
