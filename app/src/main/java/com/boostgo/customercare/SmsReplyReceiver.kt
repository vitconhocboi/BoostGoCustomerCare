package com.boostgo.customercare

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.boostgo.customercare.repo.SmsMessageInterface
import com.boostgo.customercare.repo.SettingConfigInterface
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
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
                sendTelegramMessage(telegramMessage)
            } catch (e: Exception) {
                Log.e("SmsReplyReceiver", "Error sending order info to Telegram: ${e.message}")
            }
        }
    }

    private fun sendUnknownReplyToTelegram(phoneNumber: String, replyMessage: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val telegramMessage = buildUnknownReplyMessage(phoneNumber, replyMessage)
                sendTelegramMessage(telegramMessage)
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

    private suspend fun sendTelegramMessage(message: String) {
        try {
            // Get Telegram configuration from database
            val config = settingConfigRepo.getConfig().first()

            if (config == null || config.telegramBotToken.isEmpty() || config.telegramChatId.isEmpty()) {
                Log.w(
                    "SmsReplyReceiver",
                    "Telegram configuration not found or incomplete. Skipping Telegram notification."
                )
                Log.w(
                    "SmsReplyReceiver",
                    "Bot Token: ${config?.telegramBotToken?.isNotEmpty() == true}"
                )
                Log.w(
                    "SmsReplyReceiver",
                    "Chat ID: ${config?.telegramChatId?.isNotEmpty() == true}"
                )
                return
            }

            val telegramApiUrl = "https://api.telegram.org/bot${config.telegramBotToken}/sendMessage"
            val url = URL(telegramApiUrl)
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true

            // Create JSON payload
            val jsonPayload = JSONObject().apply {
                put("chat_id", config.telegramChatId)
                put("parse_mode", "HTML")
                put("text", message)
            }

            Log.e("SmsReplyReceiver", "Send to telegram $telegramApiUrl body: $jsonPayload")

            // Send JSON data
            connection.outputStream.use { outputStream ->
                val writer = OutputStreamWriter(outputStream, "UTF-8")
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d("SmsReplyReceiver", "Message sent to Telegram successfully")
                Log.d("SmsReplyReceiver", "JSON payload: ${jsonPayload.toString()}")
            } else {
                Log.e(
                    "SmsReplyReceiver",
                    "Failed to send message to Telegram. Response code: $responseCode"
                )
                val errorResponse = connection.errorStream?.bufferedReader()?.readText()
                Log.e("SmsReplyReceiver", "Error response: $errorResponse")
            }

            connection.disconnect()
        } catch (e: IOException) {
            Log.e("SmsReplyReceiver", "Network error sending to Telegram: ${e.message}")
        } catch (e: Exception) {
            Log.e("SmsReplyReceiver", "Error sending to Telegram: ${e.message}")
        }
    }
}
