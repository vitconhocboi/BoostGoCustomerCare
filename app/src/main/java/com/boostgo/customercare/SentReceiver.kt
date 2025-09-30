package com.boostgo.customercare

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.widget.Toast
import com.boostgo.customercare.database.SmsDatabase
import com.boostgo.customercare.database.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("SentReceiver", "SMS send resultCode: $resultCode")
        
        val (message, status) = when (resultCode) {
            Activity.RESULT_OK -> {
                "SMS sent successfully" to "Sent"
            }
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> {
                "SMS send failed: Generic failure" to "Failed"
            }
            SmsManager.RESULT_ERROR_NO_SERVICE -> {
                "SMS send failed: No service" to "Failed"
            }
            SmsManager.RESULT_ERROR_NULL_PDU -> {
                "SMS send failed: Null PDU" to "Failed"
            }
            SmsManager.RESULT_ERROR_RADIO_OFF -> {
                "SMS send failed: Radio off" to "Failed"
            }
            else -> {
                "SMS send failed: Unknown error ($resultCode)" to "Failed"
            }
        }
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        android.util.Log.i("SentReceiver", message)
        
        // Update database with status
        updateMessageStatus(context, status)
    }
    
    private fun updateMessageStatus(context: Context, status: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = SmsDatabase.getDatabase(context)
                val messages = database.smsMessageDao().getAllMessages()
                // Update the most recent message with "Sending" status
                messages.collect { messageList ->
                    val recentMessage = messageList.firstOrNull { it.status == "Sending" }
                    recentMessage?.let {
                        val updatedMessage = it.copy(status = status)
                        database.smsMessageDao().updateMessage(updatedMessage)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SentReceiver", "Error updating message status: ${e.message}")
            }
        }
    }
}
