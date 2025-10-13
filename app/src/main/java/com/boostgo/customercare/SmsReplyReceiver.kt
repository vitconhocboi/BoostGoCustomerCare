package com.boostgo.customercare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.boostgo.customercare.repo.SettingConfigInterface
import com.boostgo.customercare.repo.SmsMessageInterface
import com.boostgo.customercare.utils.TelegramUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SmsReplyReceiver : BroadcastReceiver() {

    @Inject
    lateinit var smsMessageRepo: SmsMessageInterface

    @Inject
    lateinit var settingConfigRepo: SettingConfigInterface

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            for (message in messages) {
                val phoneNumber = message.originatingAddress ?: "Unknown"
                val messageBody = message.messageBody ?: ""
                val timestamp = message.timestampMillis

                Log.d("SmsReplyReceiver", "Received SMS from $phoneNumber: $messageBody")

                // Find orderId from stored message database and log order information
                findAndLogOrderInformation(phoneNumber, messageBody)
            }
        }
    }

    private fun findAndLogOrderInformation(phoneNumber: String, replyMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Find the latest message sent to this phone number (trying different variations)
                Log.d("SmsReplyReceiver", "Searching for order with phone number: $phoneNumber")
                val latestMessage = smsMessageRepo.getLatestMessageByPhone(phoneNumber)

                if (latestMessage != null) {
                    Log.d("SmsReplyReceiver", "=== ORDER INFORMATION FOUND ===")
                    Log.d("SmsReplyReceiver", "Order ID: ${latestMessage.orderId}")
                    Log.d("SmsReplyReceiver", "Phone Number: ${latestMessage.phoneNumber}")
                    Log.d("SmsReplyReceiver", "Original Message: ${latestMessage.message}")
                    Log.d("SmsReplyReceiver", "Message Status: ${latestMessage.status}")
                    Log.d("SmsReplyReceiver", "Message Timestamp: ${latestMessage.timestamp}")
                    Log.d("SmsReplyReceiver", "Delivered At: ${latestMessage.deliveredAt}")
                    Log.d("SmsReplyReceiver", "Reply Message: $replyMessage")
                    Log.d("SmsReplyReceiver", "=== END ORDER INFORMATION ===")

                    // Send order information to Telegram group
                    sendOrderInfoToTelegram(latestMessage, replyMessage)
                } else {
                    Log.w(
                        "SmsReplyReceiver",
                        "No previous message found for phone number: $phoneNumber"
                    )
                    Log.d("SmsReplyReceiver", "Reply from unknown number: $phoneNumber")
                    Log.d("SmsReplyReceiver", "Reply content: $replyMessage")

                    // Send unknown reply to Telegram
                    sendUnknownReplyToTelegram(phoneNumber, replyMessage)
                }
            } catch (e: Exception) {
                Log.e("SmsReplyReceiver", "Error finding order information: ${e.message}")
            }
        }
    }

    private fun sendOrderInfoToTelegram(
        message: com.boostgo.customercare.database.SmsMessage,
        replyMessage: String
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val telegramMessage = buildOrderInfoMessage(message, replyMessage)
                val config = settingConfigRepo.getConfig().first()
                TelegramUtils.sendTelegramMessage(telegramMessage, config)
            } catch (e: Exception) {
                Log.e("SmsReplyReceiver", "Error sending order info to Telegram: ${e.message}")
            }
        }
    }

    private fun sendUnknownReplyToTelegram(phoneNumber: String, replyMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val telegramMessage = buildUnknownReplyMessage(phoneNumber, replyMessage)
                val config = settingConfigRepo.getConfig().first()
                TelegramUtils.sendTelegramMessage(telegramMessage, config)
            } catch (e: Exception) {
                Log.e("SmsReplyReceiver", "Error sending unknown reply to Telegram: ${e.message}")
            }
        }
    }

    private fun buildOrderInfoMessage(
        message: com.boostgo.customercare.database.SmsMessage,
        replyMessage: String
    ): String {
        return """
            🔔 <b>SMS Reply Received</b>
            
            📋 <b>Order Information:</b>
            • Order ID: <code>${message.orderId}</code>
            • Phone: <code>${message.phoneNumber}</code>
            • Status: <code>${message.status}</code>
            
            💬 <b>Customer Reply:</b>
            <i>${replyMessage}</i>
            
            ⏰ <b>Timestamp:</b> ${
            java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
        }
        """.trimIndent()
    }

    private fun buildUnknownReplyMessage(phoneNumber: String, replyMessage: String): String {
        return """
            🔔 <b>Unknown SMS Reply</b>
            📱 <b>Phone Number:</b> <code>${phoneNumber}</code>
            💬 <b>Message:</b> <i>${replyMessage}</i>
            ⚠️ <b>Note:</b> No previous order found for this number
            
            ⏰ <b>Timestamp:</b> ${
            java.text.SimpleDateFormat(
                "dd/MM/yyyy HH:mm:ss",
                java.util.Locale.getDefault()
            ).format(java.util.Date())
        }
        """.trimIndent()
    }
}
