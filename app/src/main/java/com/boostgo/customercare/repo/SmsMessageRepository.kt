package com.boostgo.customercare.repo

import com.boostgo.customercare.database.SmsDatabase
import com.boostgo.customercare.database.SmsMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SmsMessageRepository @Inject constructor(private val database: SmsDatabase) :
    SmsMessageInterface {
    override suspend fun deleteAllMessages() {
        database.smsMessageDao().deleteAllMessages()
    }

    override suspend fun insertMessage(message: SmsMessage): Long {
        return database.smsMessageDao().insertMessage(message)
    }

    override suspend fun updateMessage(message: SmsMessage) {
        database.smsMessageDao().updateMessage(message)
    }

    override fun getAllMessages(): Flow<List<SmsMessage>> {
        return database.smsMessageDao().getAllMessages()
    }

    override suspend fun getMessageById(id: Long): SmsMessage? {
        return database.smsMessageDao().getMessageById(id)
    }
}