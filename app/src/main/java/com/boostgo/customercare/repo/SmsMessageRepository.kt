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

    override suspend fun getLatestMessageByPhone(phoneNumber: String): SmsMessage? {
        val phoneVariations = generatePhoneVariations(phoneNumber)

        return database.smsMessageDao().getLatestMessageByPhoneVariations(
            phoneNumber = phoneVariations.original,
            phoneWithPlus = phoneVariations.withPlus,
            phoneWithZero = phoneVariations.withZero,
            phoneWith84 = phoneVariations.with84,
            phoneWithoutPrefix = phoneVariations.withoutPrefix
        )
    }

    private fun generatePhoneVariations(phoneNumber: String): PhoneVariations {
        val cleaned = phoneNumber.replace(Regex("[^+\\d]"), "")
        
        val coreNumber = when {
            cleaned.startsWith("+84") -> cleaned.substring(3)
            cleaned.startsWith("84") -> cleaned.substring(2)
            cleaned.startsWith("0") -> cleaned.substring(1)
            else -> cleaned
        }
        
        return PhoneVariations(
            original = phoneNumber,
            withPlus = "+84$coreNumber",
            withZero = "0$coreNumber",
            with84 = "84$coreNumber",
            withoutPrefix = coreNumber
        )
    }

    private data class PhoneVariations(
        val original: String,
        val withPlus: String,
        val withZero: String,
        val with84: String,
        val withoutPrefix: String
    )
}