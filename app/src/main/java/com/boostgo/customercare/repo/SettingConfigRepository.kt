package com.boostgo.customercare.repo

import com.boostgo.customercare.database.SmsDatabase
import com.boostgo.customercare.database.TestConfig
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SettingConfigRepository @Inject constructor(private val database: SmsDatabase) :
    SettingConfigInterface {
    override fun getConfig(): Flow<TestConfig?> {
        return database.testConfigDao().getTestConfig()
    }

    override suspend fun insertConfig(testConfig: TestConfig) {
        database.testConfigDao().insertTestConfig(testConfig)
    }
}