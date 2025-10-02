package com.boostgo.customercare.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsMessageDao {
    @Query("SELECT * FROM sms_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE status = 'Delivered' ORDER BY timestamp DESC")
    fun getDeliveredMessages(): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE phoneNumber = :phoneNumber ORDER BY timestamp DESC")
    fun getMessagesByPhone(phoneNumber: String): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: Long): SmsMessage?

    @Insert
    suspend fun insertMessage(message: SmsMessage): Long

    @Update
    suspend fun updateMessage(message: SmsMessage)

    @Delete
    suspend fun deleteMessage(message: SmsMessage)

    @Query("DELETE FROM sms_messages")
    suspend fun deleteAllMessages(): Int
}
