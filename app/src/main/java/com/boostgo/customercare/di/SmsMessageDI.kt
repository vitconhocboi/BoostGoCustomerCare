package com.boostgo.customercare.di

import com.boostgo.customercare.repo.SmsMessageInterface
import com.boostgo.customercare.repo.SmsMessageRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class SmsMessageDI {
    @Provides
    @Singleton
    fun provideProxyRepository(repository: SmsMessageRepository): SmsMessageInterface {
        return repository
    }
}