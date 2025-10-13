package com.boostgo.customercare.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "sms_messages")
data class SmsMessage(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,
    @ColumnInfo(name = "phoneNumber")
    val phoneNumber: String,
    @ColumnInfo(name = "message")
    val message: String,
    @ColumnInfo(name = "status")
    val status: String, // "Sending", "Sent", "Delivered", "Failed"
    @ColumnInfo(name = "orderId")
    val orderId: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deliveredAt")
    val deliveredAt: Long? = null,
    @ColumnInfo(name = "selectedSimId")
    val selectedSimId: Int? = null, // SIM subscription ID used for sending
    @ColumnInfo(name = "sendPhoneNumber")
    val sendPhoneNumber: String? = null // Phone number of the SIM used for sending
)
