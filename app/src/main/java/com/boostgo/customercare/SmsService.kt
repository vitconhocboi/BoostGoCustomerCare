package com.boostgo.customercare

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
        const val ACTION_SMS_SENT = "com.boostgo.customercare.action.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.boostgo.customercare.SMS_DELIVERED"
        const val EXTRA_IS_LAST_PART = "com.boostgo.customercare.IS_LAST_PART"
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

                val randomDelay = (60_000L..120_000L).random() // 1-2 minutes in milliseconds
                delay(randomDelay)
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

    private suspend fun createSmsMessage(order: com.boostgo.customercare.model.SmsOrder): String {
        val config = settingConfigRepo.getConfig().first()
        val template = config?.messageTemplate ?: """Cảm ơn A/C đã đặt hàng {description}.
Shop đã tiếp nhận đơn hàng & gửi theo địa chỉ: {address}.
Đơn hàng được gửi từ kho Bach Linh, dự kiến giao từ 3–5 ngày.
A/C vui lòng để ý điện thoại giúp Shop ạ.
Mọi thắc mắc LH: 0973807248"""
        
        return template
            .replace("{description}", order.description)
            .replace("{address}", order.address)
            .replace("{name}", order.name)
            .replace("{quantity}", order.quantity.toString())
            .replace("{cod}", order.cod.toString())
    }

    fun sendMessage(context: Context, address: String, body: String, messageId: Long) {
        val smsManager = SmsManager.getDefault()
        val parts = smsManager.divideMessage(body)
        val partCount = parts.size

        val send = arrayListOf<PendingIntent>()
        val delivery = arrayListOf<PendingIntent?>()

        for (partNumber in 1..partCount) {
            val isLastPart = partNumber == partCount
            send += PendingIntent.getBroadcast(
                context,
                partNumber,
                createResultIntent(context, messageId)
                    .setAction(ACTION_SMS_SENT)
                    .putExtra(EXTRA_IS_LAST_PART, isLastPart),
                RESULT_FLAGS
            )
            delivery += if (isLastPart) {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    createResultIntent(context, messageId)
                        .setAction(ACTION_SMS_DELIVERED),
                    RESULT_FLAGS
                )
            } else null
        }

        smsManager.sendMultipartTextMessage(address, null, parts, send, delivery)
    }

    private fun createResultIntent(context: Context, messageId: Long) = Intent(
        null,
        Uri.fromParts("app", "com.boostgo.customercare", messageId.toString()),
        context,
        SmsResultReceiver::class.java
    )

    private val RESULT_FLAGS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE
    } else {
        PendingIntent.FLAG_ONE_SHOT
    }

    private fun sendSms(phoneNumber: String, message: String, orderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Check SMS permission before sending
                if (!PermissionHelper.hasSmsPermission(this@SmsService)) {
                    Log.e("SmsService", "SMS permission not granted")
                    storeMessage(phoneNumber, message, "Failed - No SMS permission", orderId)
                    return@launch
                }

                startForegroundService()

                // Store message in database and get the message ID
                val messageId = storeMessage(phoneNumber, message, "Sending", orderId)

                if (messageId != -1L) {
                    //send message with the stored message ID
                    sendMessage(this@SmsService, phoneNumber, message,  messageId)
                    // Update order status to "sent"
                    Log.i("SmsService", "SMS sent to $phoneNumber with message ID: $messageId")
                } else {
                    Log.e("SmsService", "Failed to store message in database")
                }

            } catch (e: Exception) {
                Log.e("SmsService", "Error sending SMS: ${e.message}")
                storeMessage(phoneNumber, message, "Failed", orderId)
            }
        }
    }

    private suspend fun storeMessage(phoneNumber: String, message: String, status: String, orderId: String): Long {
        return try {
            val smsMessage = SmsMessage(
                phoneNumber = phoneNumber,
                message = message,
                status = status,
                orderId = orderId
            )
            val messageId = smsMessageRepo.insertMessage(smsMessage)
            Log.d("SmsService", "Message stored: $phoneNumber - $status with ID: $messageId")
            messageId
        } catch (e: Exception) {
            Log.e("SmsService", "Error storing message: ${e.message}")
            -1L
        }
    }
}
