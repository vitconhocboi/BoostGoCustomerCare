package com.boostgo.customercare

import android.annotation.SuppressLint
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
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.boostgo.customercare.database.SmsMessage
import com.boostgo.customercare.network.ApiService
import com.boostgo.customercare.repo.SettingConfigInterface
import com.boostgo.customercare.repo.SmsMessageInterface
import com.boostgo.customercare.utils.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

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

        // Timeout constants
        const val API_TIMEOUT_MS = 30_000L // 30 seconds for API calls
        const val POLLING_TIMEOUT_MS = 300_000L // 2 minutes for overall polling operation
    }

    private lateinit var notificationManager: NotificationManager
    private var isPolling = false
    private var pollingJob: kotlinx.coroutines.Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

        pollingJob = serviceScope.launch {
            while (isPolling) {
                try {
                    Log.d("SmsService", "Starting polling cycle with timeout: ${API_TIMEOUT_MS}ms")
                    withTimeout(POLLING_TIMEOUT_MS) {
                        fetchAndSendSms(imei, token)
                        if (isPolling) {
                            val randomDelay =
                                (60_000L..120_000L).random() // 1-2 minutes in milliseconds
                            Log.d("SmsService", "Waiting ${randomDelay}ms before next poll")
                            delay(randomDelay)
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    Log.e(
                        "SmsService",
                        "API call timed out after ${API_TIMEOUT_MS}ms: ${e.message}"
                    )
                } catch (e: Exception) {
                    Log.e("SmsService", "Error in polling: ${e.message}")
                }
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
            Log.d("SmsService", "Fetching configuration with timeout: ${API_TIMEOUT_MS}ms")
            val testConfig = withTimeout(API_TIMEOUT_MS) {
                settingConfigRepo.getConfig().first()
            }
            Log.d("SmsService", "fetchAndSendSms: $testConfig")

            Log.d("SmsService", "API Request - getNewOrderSms: imei=$imei, token=$token")
            val response = withTimeout(API_TIMEOUT_MS) {
                apiService.getNewOrderSms(imei, token)
            }

            if (response.isSuccessful) {
                val order = response.body()?.result
                Log.d("SmsService", "API Response Body: ${response.body()}")

                if (order != null) {
                    Log.d(
                        "SmsService",
                        "Fetched new order: id=${order.orderId}, name=${order.name}, number=${order.number}, description=${order.description}, quantity=${order.quantity}, cod=${order.cod}, status=${order.status}"
                    )

                    val message = createSmsMessage(order)
                    val sendToNumber = if (testConfig?.isTestingEnabled == true) {
                        testConfig.testNumber
                    } else {
                        order.number
                    }
                    Log.d(
                        "SmsService",
                        "Sending SMS to: $sendToNumber (test mode: ${testConfig?.isTestingEnabled})"
                    )
                    withTimeout(API_TIMEOUT_MS)  {
                        sendSms(sendToNumber, message, order.orderId)
                    }
                } else {
                    Log.d("SmsService", "No new orders found")
                }
            } else {
                Log.e("SmsService", "API call failed: ${response.message()}")
                Log.e("SmsService", "Response body: ${response.body()}")
            }

        } catch (e: TimeoutCancellationException) {
            Log.e("SmsService", "fetchAndSendSms timed out after ${API_TIMEOUT_MS}ms: ${e.message}")
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

    fun sendMessage(context: Context, address: String, body: String, messageId: Long): Pair<Int, String?> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
        @SuppressLint("MissingPermission")
        val activeSubs = if(PermissionHelper.hasReadSmsPermission(context))
            subscriptionManager.activeSubscriptionInfoList?:emptyList()
        else emptyList()
        Log.d("SmsService", "Active sim ${activeSubs.size}")
        var selectSim: Int = SmsManager.getDefaultSmsSubscriptionId()
        var sendPhoneNumber: String? = null
        val carrier = getNetworkOperator(address)
        for (sub in activeSubs) {
            if (sub.carrierName != null && sub.carrierName.toString().equals(carrier, ignoreCase = true)) {
                selectSim = sub.subscriptionId
                sendPhoneNumber = sub.number
                break
            }
            Log.d("SmsService", "Display Name: ${sub.displayName}, Number: ${sub.number}, ID: ${sub.subscriptionId}")
        }
        
        // If no matching carrier found, get the phone number from the selected SIM
        if (sendPhoneNumber == null) {
            for (sub in activeSubs) {
                if (sub.subscriptionId == selectSim) {
                    sendPhoneNumber = sub.number
                    break
                }
            }
        }
        
        Log.d("SmsService", "Selected SIM ID: $selectSim, Send Phone: $sendPhoneNumber")
        
        val smsManager = SmsManager.getSmsManagerForSubscriptionId(selectSim)
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
        return Pair(selectSim, sendPhoneNumber)
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
        CoroutineScope(Dispatchers.IO).launch  {
            try {
                // Check SMS permission before sending
                if (!PermissionHelper.hasSmsPermission(this@SmsService)) {
                    Log.e("SmsService", "SMS permission not granted")
                    storeMessage(phoneNumber, message, "Failed - No SMS permission", orderId)
                    return@launch
                }

                startForegroundService()

                // Store message in database and get the message ID (without SIM info initially)
                val messageId = storeMessage(phoneNumber, message, "Sending", orderId)

                if (messageId != -1L) {
                    // Send message and get SIM information
                    val (selectedSimId, sendPhoneNumber) = sendMessage(this@SmsService, phoneNumber, message, messageId)
                    
                    // Update the message with SIM information
                    updateMessageWithSimInfo(messageId, selectedSimId, sendPhoneNumber)
                    
                    // Update order status to "sent"
                    Log.i("SmsService", "SMS sent to $phoneNumber with message ID: $messageId, SIM: $selectedSimId, Send Phone: $sendPhoneNumber")
                } else {
                    Log.e("SmsService", "Failed to store message in database")
                }

            } catch (e: Exception) {
                Log.e("SmsService", "Error sending SMS: ${e.message}")
                storeMessage(phoneNumber, message, "Failed", orderId)
            }
        }
    }

    private suspend fun storeMessage(
        phoneNumber: String,
        message: String,
        status: String,
        orderId: String,
        selectedSimId: Int? = null,
        sendPhoneNumber: String? = null
    ): Long {
        return try {
            val smsMessage = SmsMessage(
                phoneNumber = phoneNumber,
                message = message,
                status = status,
                orderId = orderId,
                selectedSimId = selectedSimId,
                sendPhoneNumber = sendPhoneNumber
            )
            val messageId = smsMessageRepo.insertMessage(smsMessage)
            Log.d("SmsService", "Message stored: $phoneNumber - $status with ID: $messageId, SIM: $selectedSimId, Send Phone: $sendPhoneNumber")
            messageId
        } catch (e: Exception) {
            Log.e("SmsService", "Error storing message: ${e.message}")
            -1L
        }
    }
    
    private suspend fun updateMessageWithSimInfo(messageId: Long, selectedSimId: Int, sendPhoneNumber: String?) {
        try {
            val message = smsMessageRepo.getMessageById(messageId)
            if (message != null) {
                val updatedMessage = message.copy(
                    selectedSimId = selectedSimId,
                    sendPhoneNumber = sendPhoneNumber
                )
                smsMessageRepo.updateMessage(updatedMessage)
                Log.d("SmsService", "Updated message $messageId with SIM info: $selectedSimId, $sendPhoneNumber")
            }
        } catch (e: Exception) {
            Log.e("SmsService", "Error updating message with SIM info: ${e.message}")
        }
    }


    fun getNetworkOperator(phoneNumber: String): String {
        // Remove all non-digit characters and normalize the number
        val cleanNumber = phoneNumber.replace(Regex("[^0-9]"), "")
        
        // Remove country code if present (Vietnam is +84)
        val normalizedNumber = if (cleanNumber.startsWith("84") && cleanNumber.length >= 10) {
            cleanNumber.substring(2)
        } else if (cleanNumber.startsWith("0") && cleanNumber.length >= 10) {
            cleanNumber.substring(1)
        } else {
            cleanNumber
        }
        
        // Check if the number is valid Vietnamese mobile number (9-10 digits after normalization)
        if (normalizedNumber.length < 9 || normalizedNumber.length > 10) {
            return "UNKNOWN"
        }
        
        // Get the first 3-4 digits to identify the carrier
        val prefix = if (normalizedNumber.length >= 4) {
            normalizedNumber.substring(0, 4)
        } else {
            normalizedNumber.substring(0, 3)
        }
        
        return when {
            // Viettel prefixes
            prefix.startsWith("096") || prefix.startsWith("097") || prefix.startsWith("098") ||
            prefix.startsWith("032") || prefix.startsWith("033") || prefix.startsWith("034") ||
            prefix.startsWith("035") || prefix.startsWith("036") || prefix.startsWith("037") ||
            prefix.startsWith("038") || prefix.startsWith("039") || prefix.startsWith("086") ||
            prefix.startsWith("081") || prefix.startsWith("082") || prefix.startsWith("083") ||
            prefix.startsWith("084") || prefix.startsWith("085") -> "Viettel"
            
            // Vinaphone prefixes
            prefix.startsWith("088") || prefix.startsWith("091") || prefix.startsWith("094") ||
            prefix.startsWith("081") || prefix.startsWith("082") || prefix.startsWith("083") ||
            prefix.startsWith("084") || prefix.startsWith("085") || prefix.startsWith("086") ||
            prefix.startsWith("087") || prefix.startsWith("089") || prefix.startsWith("090") ||
            prefix.startsWith("093") || prefix.startsWith("095") || prefix.startsWith("096") ||
            prefix.startsWith("097") || prefix.startsWith("098") || prefix.startsWith("099") -> "VINAPHONE"
            
            // Mobifone prefixes
            prefix.startsWith("089") || prefix.startsWith("090") || prefix.startsWith("093") ||
            prefix.startsWith("070") || prefix.startsWith("076") || prefix.startsWith("077") ||
            prefix.startsWith("078") || prefix.startsWith("079") -> "Mobiphone"
            
            // Vietnamobile prefixes
            prefix.startsWith("092") || prefix.startsWith("056") || prefix.startsWith("058") -> "Vietnamobile"
            
            // Gmobile prefixes
            prefix.startsWith("059") || prefix.startsWith("099") -> "Gmobile"
            
            else -> "UNKNOWN"
        }
    }
}
