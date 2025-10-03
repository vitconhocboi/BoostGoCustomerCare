package com.boostgo.customercare.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [SmsMessage::class, TestConfig::class],
    version = 4,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE sms_messages ADD COLUMN orderId TEXT NOT NULL DEFAULT ''
                """)
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    ALTER TABLE test_config ADD COLUMN message_template TEXT NOT NULL DEFAULT 'Cảm ơn A/C đã đặt hàng {description}.
Shop đã tiếp nhận đơn hàng & gửi theo địa chỉ: {address}.
Đơn hàng được gửi từ kho Bach Linh, dự kiến giao từ 3–5 ngày.
A/C vui lòng để ý điện thoại giúp Shop ạ.
Mọi thắc mắc LH: 0973807248'
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
