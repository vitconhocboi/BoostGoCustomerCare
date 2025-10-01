package com.boostgo.customercare.repo

import com.boostgo.customercare.database.TestConfig
import kotlinx.coroutines.flow.Flow

interface SettingConfigInterface {
    fun getConfig(): Flow<TestConfig?>

   suspend fun insertConfig(testConfig: TestConfig)
}