package com.boostgo.customercare.di

import com.boostgo.customercare.repo.SettingConfigInterface
import com.boostgo.customercare.repo.SettingConfigRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SettingConfigDI {
    @Provides
    @Singleton
    fun provideProxyRepository(repository: SettingConfigRepository): SettingConfigInterface {
        return repository
    }
}