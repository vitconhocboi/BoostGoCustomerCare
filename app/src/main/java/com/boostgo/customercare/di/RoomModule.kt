package com.boostgo.customercare.di

import android.content.Context
import androidx.room.Room
import com.boostgo.customercare.database.SmsDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class RoomModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SmsDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            SmsDatabase::class.java,
            "sms_database"
        ).addMigrations(
            SmsDatabase.Companion.MIGRATION_1_2,
            SmsDatabase.Companion.MIGRATION_2_3,
            SmsDatabase.Companion.MIGRATION_3_4,
            SmsDatabase.Companion.MIGRATION_4_5,
            SmsDatabase.Companion.MIGRATION_5_6
        ).build()
    }
}