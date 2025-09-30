package com.boostgo.customercare.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [SmsMessage::class],
    version = 1,
    exportSchema = false
)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao
    
    companion object {
        @Volatile
        private var INSTANCE: SmsDatabase? = null
        
        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "sms_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
