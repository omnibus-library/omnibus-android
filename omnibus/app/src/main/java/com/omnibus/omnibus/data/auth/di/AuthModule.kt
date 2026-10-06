package com.omnibus.omnibus.data.auth.di

import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.DefaultAuthRepository
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.local.DefaultAuthTokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
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

    @Binds
    @Singleton
    abstract fun bindAuthTokenStore(
        tokenStore: DefaultAuthTokenStore,
    ): AuthTokenStore

    companion object {
        @Provides
        @IoDispatcher
        fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @BaseUrl
        //fixme: read this from the server config; not use hardcoded value
        fun provideBaseUrl(): String = "https://read.h00n.dev/"
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseUrl

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
