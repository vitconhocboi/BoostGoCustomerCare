package com.boostgo.customercare.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.boostgo.customercare.repo.SettingConfigInterface
import com.boostgo.customercare.database.TestConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TestConfigViewModel @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@Inject constructor(private val settingConfigRepo: SettingConfigInterface) : ViewModel() {

    fun getTestConfig(): Flow<TestConfig?> = settingConfigRepo.getConfig()

    fun saveTestConfig(testConfig: TestConfig) {
        viewModelScope.launch {
            settingConfigRepo.insertConfig(testConfig)
        }
    }
}
