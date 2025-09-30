package com.boostgo.customercare

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.boostgo.customercare.database.SmsDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d("DeliveredReceiver", "SMS delivery resultCode: $resultCode")
        
        val (message, status) = when (resultCode) {
            Activity.RESULT_OK -> {
                "SMS delivered successfully" to "Delivered"
            }
            else -> {
                "SMS delivery failed" to "Failed"
            }
        }
        
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        android.util.Log.i("DeliveredReceiver", message)
        
        // Update database with delivery status
        updateDeliveryStatus(context, status)
    }
    
    private fun updateDeliveryStatus(context: Context, status: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = SmsDatabase.getDatabase(context)
                val messages = database.smsMessageDao().getAllMessages()
                // Update the most recent message with "Sent" status
                messages.collect { messageList ->
                    val recentMessage = messageList.firstOrNull { it.status == "Sent" }
                    recentMessage?.let {
                        val updatedMessage = it.copy(
                            status = status,
                            deliveredAt = if (status == "Delivered") System.currentTimeMillis() else null
                        )
                        database.smsMessageDao().updateMessage(updatedMessage)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("DeliveredReceiver", "Error updating delivery status: ${e.message}")
            }
        }
    }
}
