package com.omnibus.omnibus.data.auth.di

import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.auth.DefaultAuthRepository
import com.omnibus.omnibus.data.auth.local.AuthTokenStore
import com.omnibus.omnibus.data.auth.local.DefaultAuthTokenStore
import com.omnibus.omnibus.data.auth.local.StoredAuthTokenProvider
import com.omnibus.omnibus.data.auth.remote.ClientInfo
import com.omnibus.omnibus.data.auth.remote.DefaultClientInfo
import com.omnibus.omnibus.data.network.AuthTokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

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
        store: DefaultAuthTokenStore,
    ): AuthTokenStore

    @Binds
    @Singleton
    abstract fun bindAuthTokenProvider(
        provider: StoredAuthTokenProvider,
    ): AuthTokenProvider

    @Binds
    abstract fun bindClientInfo(
        clientInfo: DefaultClientInfo,
    ): ClientInfo
}
