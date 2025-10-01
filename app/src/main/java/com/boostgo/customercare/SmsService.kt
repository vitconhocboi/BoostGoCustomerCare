package com.boostgo.customercare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.boostgo.customercare.database.SmsDatabase
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.utils.PermissionHelper
import com.boostgo.customercare.repo.SettingConfigInterface
import com.boostgo.customercare.repo.SmsMessageInterface
import com.boostgo.customercare.network.ApiService
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class SmsService : Service() {

    @Inject
    lateinit var settingConfigRepo: SettingConfigInterface
    
    @Inject
    lateinit var smsMessageRepo: SmsMessageInterface
    
    @Inject
    lateinit var apiService: ApiService



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

    private fun startForegroundService() {
        val notificationIntent = Intent(this, HomeFragment::class.java)
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
            val testConfig = settingConfigRepo.getConfig().first()
            Log.d("SmsService", "fetchAndSendSms: $testConfig")
            
            Log.d("SmsService", "API Request - getNewOrderSms: imei=$imei, token=$token")
            val response = apiService.getNewOrderSms(imei, token)

            if (response.isSuccessful) {
                val order = response.body()?.result
                Log.d("SmsService", "API Response Body: ${response.body()}")
                
                if (order != null) {
                    Log.d("SmsService", "Fetched new order: id=${order.orderId}, name=${order.name}, number=${order.number}, description=${order.description}, quantity=${order.quantity}, cod=${order.cod}, status=${order.status}")
                    
                    val message = createSmsMessage(order)
                    val sendToNumber = if (testConfig?.isTestingEnabled == true) {
                        testConfig.testNumber
                    } else {
                        order.number
                    }
                    Log.d("SmsService", "Sending SMS to: $sendToNumber (test mode: ${testConfig?.isTestingEnabled})")
                    sendSms(sendToNumber, message, order.orderId)
                } else {
                    Log.d("SmsService", "No new orders found")
                }
            } else {
                Log.e("SmsService", "API call failed: ${response.message()}")
                Log.e("SmsService", "Response body: ${response.body()}")
            }

        } catch (e: Exception) {
            Log.e("SmsService", "Error in fetchAndSendSms: ${e.message}")
        }
    }

    private fun createSmsMessage(order: com.boostgo.customercare.model.SmsOrder): String {
        return """
            Chào A/C ${order.name} ạ!
            Anh chị có đặt bên em ${order.description}. 
            Số lượng: ${order.quantity} - COD: ${order.cod}đ
            Shop đã tiếp nhận đơn hàng của A/C.
            Shop sẽ gửi hàng cho A/C theo địa chỉ ${order.address}.
            Hàng sẽ về từ 3 đến 5 ngày A/C để ý điện thoại nhận hàng giúp Shop ạ.
            Cảm ơn A/C!
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
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, arrayListOf(sentPI), arrayListOf(deliveredPI))
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
                val smsMessage = SmsMessage(
                    phoneNumber = phoneNumber,
                    message = message,
                    status = status
                )
                smsMessageRepo.insertMessage(smsMessage)
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

                val response = apiService.updateOrderSms(request)

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
