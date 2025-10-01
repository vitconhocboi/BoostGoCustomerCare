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

    override suspend fun insertMessage(message: SmsMessage) {
        database.smsMessageDao().insertMessage(message)
    }

    override fun getAllMessages(): Flow<List<SmsMessage>> {
        return database.smsMessageDao().getAllMessages()
    }
}