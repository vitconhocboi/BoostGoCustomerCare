package com.boostgo.customercare.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TestConfigDao {
    @Query("SELECT * FROM test_config WHERE id = 1")
    fun getTestConfig(): Flow<TestConfig?>
    
    @Query("SELECT * FROM test_config WHERE id = 1")
    suspend fun getTestConfigSync(): TestConfig?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTestConfig(testConfig: TestConfig)
    
    @Update
    suspend fun updateTestConfig(testConfig: TestConfig)
    
    @Query("UPDATE test_config SET test_number = :testNumber WHERE id = 1")
    suspend fun updateTestNumber(testNumber: String)
    
    @Query("UPDATE test_config SET is_testing_enabled = :isEnabled WHERE id = 1")
    suspend fun updateTestingStatus(isEnabled: Boolean)
    
}
