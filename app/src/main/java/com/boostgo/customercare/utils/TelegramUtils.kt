package com.boostgo.customercare.utils

import android.util.Log
import com.boostgo.customercare.database.TestConfig
import com.boostgo.customercare.repo.SettingConfigInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object TelegramUtils {
    /**
     * Send message to Telegram using configuration from database
     */
    suspend fun sendTelegramMessage(message: String, config: TestConfig?) {
        try {
            if (config == null || config.telegramBotToken.isEmpty() || config.telegramChatId.isEmpty()) {
                Log.w(
                    "TelegramUtils",
                    "Telegram configuration not found or incomplete. Skipping Telegram notification."
                )
                Log.w(
                    "TelegramUtils",
                    "Bot Token: ${config?.telegramBotToken?.isNotEmpty() == true}"
                )
                Log.w(
                    "TelegramUtils",
                    "Chat ID: ${config?.telegramChatId?.isNotEmpty() == true}"
                )
                return
            }

            val telegramApiUrl =
                "https://api.telegram.org/bot${config.telegramBotToken}/sendMessage"
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

            Log.d("TelegramUtils", "Sending to Telegram: $telegramApiUrl")
            Log.d("TelegramUtils", "Message: $message")

            // Send JSON data
            connection.outputStream.use { outputStream ->
                val writer = OutputStreamWriter(outputStream, "UTF-8")
                writer.write(jsonPayload.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                Log.d("TelegramUtils", "Message sent to Telegram successfully")
            } else {
                Log.e(
                    "TelegramUtils",
                    "Failed to send message to Telegram. Response code: $responseCode"
                )
                val errorResponse = connection.errorStream?.bufferedReader()?.readText()
                Log.e("TelegramUtils", "Error response: $errorResponse")
            }

            connection.disconnect()
        } catch (e: IOException) {
            Log.e("TelegramUtils", "Network error sending to Telegram: ${e.message}")
        } catch (e: Exception) {
            Log.e("TelegramUtils", "Error sending to Telegram: ${e.message}")
        }
    }
}
