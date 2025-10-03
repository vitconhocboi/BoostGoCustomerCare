package com.boostgo.customercare

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import com.boostgo.customercare.SmsService.Companion.TOKEN
import com.boostgo.customercare.model.UpdateOrderSmsRequest
import com.boostgo.customercare.network.ApiService
import com.boostgo.customercare.repo.SmsMessageInterface
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject


@AndroidEntryPoint
class SmsResultReceiver : BroadcastReceiver() {
    @Inject
    lateinit var apiService: ApiService

    @Inject
    lateinit var smsMessageRepo: SmsMessageInterface
    
    private val playedFailureSound = mutableSetOf<Long>()

    override fun onReceive(context: Context?, intent: Intent?) {
        val messageId = intent?.data?.fragment?.toLongOrNull() ?: return

        when (intent.action) {
            SmsService.Companion.ACTION_SMS_SENT -> {
                val isLastPart =
                    intent.getBooleanExtra(SmsService.Companion.EXTRA_IS_LAST_PART, false)

                if (resultCode == Activity.RESULT_OK) {
                    Log.d("SmsResultReceiver", "SMS sent successfully for message ID: $messageId")
                    updateMessageStatus(context, messageId, "Sent")
                } else {
                    Log.d("SmsResultReceiver", "SMS failed to send for message ID: $messageId, resultCode: $resultCode")
                    updateMessageStatus(context, messageId, "Failed")
                    // Play sound for failed message (only once per message)
//                    playFailureSoundOnce(context, messageId)
                }
            }
            SmsService.Companion.ACTION_SMS_DELIVERED -> {
                val status =
                    getResultMessageFromIntent(intent)?.status ?: return
                when {
                    status == Telephony.Sms.STATUS_COMPLETE -> {
                        Log.d("SmsResultReceiver", "SMS delivered successfully for message ID: $messageId")
                        updateMessageStatus(context, messageId, "Delivered")
                    }
                    status >= Telephony.Sms.STATUS_FAILED -> {
                        Log.d("SmsResultReceiver", "SMS delivery failed for message ID: $messageId")
                        updateMessageStatus(context, messageId, "Failed")
                        // Play sound for delivery failure (only once per message)
                        playFailureSoundOnce(context, messageId)
                    }
                    else -> {
                        Log.d("SmsResultReceiver", "SMS delivery pending for message ID: $messageId")
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
                    if (status == "Delivered"){
                        updateOrderStatus(message.orderId, "3")
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
            Log.d("SmsResultReceiver", "Failure sound already played for message ID: $messageId, skipping")
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
            val customSoundUri = Uri.parse("android.resource://${context.packageName}/${R.raw.fail}")
            val mediaPlayer = MediaPlayer()
            
            try {
                mediaPlayer.setDataSource(context, customSoundUri)
                mediaPlayer.prepare()
                mediaPlayer.start()
                
                Log.d("SmsResultReceiver", "Played custom fail.mp3 sound")
            } catch (e: Exception) {
                // Fallback to system notification sound if custom file not found
                Log.w("SmsResultReceiver", "Custom fail.mp3 not found, using system sound: ${e.message}")
                mediaPlayer.release()
                
                val notificationUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
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

                Log.d("SmsService", "Order $orderId updated to $status: ${response.body()}")
            } catch (e: Exception) {
                Log.e("SmsService", "Error updating order status: ${e.message}")
            }
        }
    }
}