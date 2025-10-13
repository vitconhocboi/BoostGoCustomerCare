package com.boostgo.customercare

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import com.boostgo.customercare.SmsService.Companion.TOKEN
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import com.boostgo.customercare.network.ApiService
import com.boostgo.customercare.repo.SettingConfigInterface
import com.boostgo.customercare.repo.SmsMessageInterface
import com.boostgo.customercare.utils.PermissionHelper
import com.boostgo.customercare.utils.TelegramUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.util.regex.Pattern


@AndroidEntryPoint
class SmsResultReceiver : BroadcastReceiver() {
    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var smsMessageRepo: SmsMessageInterface

    @Inject
    lateinit var settingConfigRepo: SettingConfigInterface

    private val playedFailureSound = mutableSetOf<Long>()

    // Data class for USSD response parsing
    data class UssdResponseData(
        val phoneNumber: String?,
        val balance: Long?,
        val carrier: String?
    )

    // Low balance threshold
    companion object {
        private const val LOW_BALANCE_THRESHOLD = 20000L // 20,000 VND
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val messageId = intent?.data?.fragment?.toLongOrNull() ?: return

        when (intent.action) {
            SmsService.Companion.ACTION_SMS_SENT -> {
                val isLastPart =
                    intent.getBooleanExtra(SmsService.Companion.EXTRA_IS_LAST_PART, false)
                if (isLastPart) {
                    if (resultCode == Activity.RESULT_OK) {
                        Log.d(
                            "SmsResultReceiver",
                            "SMS sent successfully for message ID: $messageId"
                        )
                        updateMessageStatus(context, messageId, "Sent")
                    } else {
                        Log.d(
                            "SmsResultReceiver",
                            "SMS failed to send for message ID: $messageId, resultCode: $resultCode"
                        )
                        updateMessageStatus(context, messageId, "Failed")
                    }
                }
            }

            SmsService.Companion.ACTION_SMS_DELIVERED -> {
                val status =
                    getResultMessageFromIntent(intent)?.status ?: return
                when {
                    status == Telephony.Sms.STATUS_COMPLETE -> {
                        Log.d(
                            "SmsResultReceiver",
                            "SMS delivered successfully for message ID: $messageId"
                        )
                        updateMessageStatus(context, messageId, "Delivered")
                    }

                    status >= Telephony.Sms.STATUS_FAILED -> {
                        Log.d("SmsResultReceiver", "SMS delivery failed for message ID: $messageId")
                        updateMessageStatus(context, messageId, "Failed")
                        // Play sound for delivery failure (only once per message)
                        playFailureSoundOnce(context, messageId)
                    }

                    else -> {
                        Log.d(
                            "SmsResultReceiver",
                            "SMS delivery pending for message ID: $messageId"
                        )
                    }
                }
            }
        }
    }

    private fun updateMessageStatus(context: Context?, messageId: Long, status: String) {
        if (context == null) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val message = smsMessageRepo.getMessageById(messageId)

                if (message != null) {
                    val updatedMessage = message.copy(
                        status = status,
                        deliveredAt = if (status == "Delivered") System.currentTimeMillis() else message.deliveredAt
                    )
                    smsMessageRepo.updateMessage(updatedMessage)
                    if (status == "Delivered") {
                        updateOrderStatus(message.orderId, "3")
                    } else if (status == "Fail") {
                        message.selectedSimId?.let { checkUssd(context, it) }
                    }
                    Log.d("SmsResultReceiver", "Updated message $messageId status to: $status")
                } else {
                    Log.w("SmsResultReceiver", "Message with ID $messageId not found in database")
                }
            } catch (e: Exception) {
                Log.e("SmsResultReceiver", "Error updating message status: ${e.message}")
            }
        }
    }

    private fun getResultMessageFromIntent(intent: Intent): android.telephony.SmsMessage? =
        android.telephony.SmsMessage.createFromPdu(
            intent.getByteArrayExtra("pdu"),
            intent.getStringExtra("format")
        )

    private fun playFailureSoundOnce(context: Context?, messageId: Long) {
        // Check if we've already played the sound for this message
        if (playedFailureSound.contains(messageId)) {
            Log.d(
                "SmsResultReceiver",
                "Failure sound already played for message ID: $messageId, skipping"
            )
            return
        }

        // Mark this message as having played the sound
        playedFailureSound.add(messageId)

        // Clean up old message IDs to prevent memory leaks (keep only last 100)
        if (playedFailureSound.size > 100) {
            val toRemove = playedFailureSound.take(playedFailureSound.size - 100)
            playedFailureSound.removeAll(toRemove)
        }

        // Play the failure sound
        playFailureSound(context)
    }

    private fun playFailureSound(context: Context?) {
        if (context == null) return

        try {
            // Try to play custom fail.mp3 file first
            val customSoundUri =
                Uri.parse("android.resource://${context.packageName}/${R.raw.fail}")
            val mediaPlayer = MediaPlayer()

            try {
                mediaPlayer.setDataSource(context, customSoundUri)
                mediaPlayer.prepare()
                mediaPlayer.start()

                Log.d("SmsResultReceiver", "Played custom fail.mp3 sound")
            } catch (e: Exception) {
                // Fallback to system notification sound if custom file not found
                Log.w(
                    "SmsResultReceiver",
                    "Custom fail.mp3 not found, using system sound: ${e.message}"
                )
                mediaPlayer.release()

                val notificationUri: Uri =
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val fallbackPlayer = MediaPlayer()
                fallbackPlayer.setDataSource(context, notificationUri)
                fallbackPlayer.prepare()
                fallbackPlayer.start()

                fallbackPlayer.setOnCompletionListener { mp ->
                    mp.release()
                }

                Log.d("SmsResultReceiver", "Played fallback system notification sound")
                return
            }

            // Release the MediaPlayer when done
            mediaPlayer.setOnCompletionListener { mp ->
                mp.release()
            }

        } catch (e: Exception) {
            Log.e("SmsResultReceiver", "Error playing failure sound: ${e.message}")
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

                Log.d("SmsResultReceiver", "Order $orderId updated to $status: ${response.body()}")
            } catch (e: Exception) {
                Log.e("SmsResultReceiver", "Error updating order status: ${e.message}")
            }
        }
    }

    /**
     * Check USSD codes to verify network connectivity and balance
     * This function is called when SMS sending fails
     */
    private fun checkUssd(context: Context?, subscriptionId: Int) {
        try {
            Log.d("SmsResultReceiver", "Starting USSD check due to SMS failure")

            val telephonyManager =
                context?.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

            // Check if we have phone state permission
            if (!PermissionHelper.hasPhoneStatePermission(context)) {
                Log.e("SmsResultReceiver", "Phone state permission not granted for USSD check")
                return
            }

            // Check if we have phone state permission
            if (!PermissionHelper.hasCallPhonePermission(context)) {
                Log.e("SmsResultReceiver", "Phone call permission not granted for USSD check")
                return
            }

            // Check each active SIM
            val ussdCodes = "*101#"

            @SuppressLint("MissingPermission")
            telephonyManager.createForSubscriptionId(subscriptionId)
                .sendUssdRequest(
                    ussdCodes,
                    object : TelephonyManager.UssdResponseCallback() {
                        override fun onReceiveUssdResponse(
                            telephonyManager: TelephonyManager?,
                            request: String?,
                            response: CharSequence?
                        ) {
                            Log.d("SmsResultReceiver", "USSD response: $response")

                            // Parse the USSD response
                            val ussdData = parseUssdResponse(response?.toString() ?: "")

                            // Check if balance is low and send Telegram notification
                            if (ussdData.balance != null && ussdData.balance < LOW_BALANCE_THRESHOLD) {
                                sendTelegramNotification(ussdData)
                            }
                        }

                        override fun onReceiveUssdResponseFailed(
                            telephonyManager: TelephonyManager?,
                            request: String?,
                            failureCode: Int
                        ) {
                            Log.e("SmsResultReceiver", "USSD request failed with code: $failureCode for request: $request")
                        }
                    },
                    null
                )
        } catch (e: Exception) {
            Log.e("SmsResultReceiver", "Error in USSD check: ${e.message}")
        }
    }

    /**
     * Parse USSD response to extract phone number and balance
     * Example response: "So TB 0858122773 (VINA690). TK chinh=184813 VND, HSD 12/12/2025..."
     */
    private fun parseUssdResponse(response: String): UssdResponseData {
        try {
            Log.d("SmsResultReceiver", "Parsing USSD response: $response")

            var phoneNumber: String? = null
            var balance: Long? = null
            var carrier: String? = null

            // Extract phone number - pattern: "So TB 0858122773"
            val phonePattern = Pattern.compile("So TB (\\d+)")
            val phoneMatcher = phonePattern.matcher(response)
            if (phoneMatcher.find()) {
                phoneNumber = phoneMatcher.group(1)
                Log.d("SmsResultReceiver", "Extracted phone number: $phoneNumber")
            }

            // Extract balance - pattern: "TK chinh=184813 VND"
            val balancePattern = Pattern.compile("TK chinh=(\\d+) VND")
            val balanceMatcher = balancePattern.matcher(response)
            if (balanceMatcher.find()) {
                balance = balanceMatcher.group(1).toLongOrNull()
                Log.d("SmsResultReceiver", "Extracted balance: $balance VND")
            }

            // Extract carrier - pattern: "(VINA690)"
            val carrierPattern = Pattern.compile("\\((\\w+)\\)")
            val carrierMatcher = carrierPattern.matcher(response)
            if (carrierMatcher.find()) {
                carrier = carrierMatcher.group(1)
                Log.d("SmsResultReceiver", "Extracted carrier: $carrier")
            }

            return UssdResponseData(phoneNumber, balance, carrier)

        } catch (e: Exception) {
            Log.e("SmsResultReceiver", "Error parsing USSD response: ${e.message}")
            return UssdResponseData(null, null, null)
        }
    }


    fun buildLowBalanceMessage(
        phoneNumber: String?,
        carrier: String?,
        balance: Long?,
        threshold: Long = 20000L
    ): String {
        return """
            🚨 <b>Cảnh báo tài khoản sắp hết tiền</b> 🚨
            📱 <b>Phone:</b> <code>${phoneNumber ?: "Unknown"}</code>
            📶 <b>Carrier:</b> <code>${carrier ?: "Unknown"}</code>
            💰 <b>Số dư:</b> <code>${balance ?: 0} VND</code>
            
            🔄 <b>Xin vui lòng nạp thêm tiền để tiếp tục sử dụng tính năng gửi tin nhắn tự động.</b>
            
            ⏰ <b>Thời gian:</b> ${
            java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
        }
        """.trimIndent()
    }

    /**
     * Send Telegram notification when balance is low
     */
    private fun sendTelegramNotification(ussdData: UssdResponseData) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = settingConfigRepo.getConfig().first()
                val message = buildLowBalanceMessage(
                    ussdData.phoneNumber,
                    ussdData.carrier,
                    ussdData.balance,
                    LOW_BALANCE_THRESHOLD
                )
                TelegramUtils.sendTelegramMessage(message, config)
                Log.d("SmsResultReceiver", "Telegram notification sent for low balance")
            } catch (e: Exception) {
                Log.e("SmsResultReceiver", "Error sending Telegram notification: ${e.message}")
            }
        }
    }
}