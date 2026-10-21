package com.omnibus.omnibus.data.auth.di

import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.DefaultAuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        repository: DefaultAuthRepository,
    ): AuthRepository

    companion object {
        @Provides
        @BaseUrl
        fun provideBaseUrl(): String = "https://read.h00n.dev/"
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl
