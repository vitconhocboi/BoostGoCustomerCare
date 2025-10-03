package com.boostgo.customercare.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "test_config")
data class TestConfig(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // Single row configuration
    @ColumnInfo(name = "test_number")
    val testNumber: String,
    @ColumnInfo(name = "is_testing_enabled")
    val isTestingEnabled: Boolean,
    @ColumnInfo(name = "message_template")
    val messageTemplate: String,
    @ColumnInfo(name = "telegram_bot_token")
    val telegramBotToken: String = "",
    @ColumnInfo(name = "telegram_chat_id")
    val telegramChatId: String = ""
)
