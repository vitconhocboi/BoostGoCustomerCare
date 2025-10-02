package com.boostgo.customercare.repo

import com.boostgo.customercare.database.SmsMessage
import kotlinx.coroutines.flow.Flow

interface SmsMessageInterface {
    fun getAllMessages(): Flow<List<SmsMessage>>
    suspend fun insertMessage(message: SmsMessage): Long
    suspend fun updateMessage(message: SmsMessage)
    suspend fun deleteAllMessages()
    suspend fun getMessageById(id: Long): SmsMessage?
}