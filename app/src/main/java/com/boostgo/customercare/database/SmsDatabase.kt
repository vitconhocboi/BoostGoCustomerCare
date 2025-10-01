package com.boostgo.customercare.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [SmsMessage::class, TestConfig::class],
    version = 2,
    exportSchema = false
)
abstract class SmsDatabase : RoomDatabase() {
    abstract fun smsMessageDao(): SmsMessageDao
    abstract fun testConfigDao(): TestConfigDao
    
    companion object {
        @Volatile
        private var INSTANCE: SmsDatabase? = null
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS test_config (
                        id INTEGER PRIMARY KEY NOT NULL,
                        test_number TEXT NOT NULL,
                        is_testing_enabled INTEGER NOT NULL
                    )
                """)
            }
        }
        
        fun getDatabase(context: Context): SmsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SmsDatabase::class.java,
                    "sms_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
