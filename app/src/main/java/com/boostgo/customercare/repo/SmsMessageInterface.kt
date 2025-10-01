package com.boostgo.customercare.repo

import com.boostgo.customercare.database.SmsMessage
import kotlinx.coroutines.flow.Flow

interface SmsMessageInterface {
    fun getAllMessages(): Flow<List<SmsMessage>>
    suspend fun insertMessage(message: SmsMessage)
    suspend fun deleteAllMessages()
}