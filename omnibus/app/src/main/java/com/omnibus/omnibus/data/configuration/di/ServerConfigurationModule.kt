package com.omnibus.omnibus.data.configuration.di

import com.omnibus.omnibus.data.configuration.DefaultServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.SavedServerBaseUrlProvider
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import com.omnibus.omnibus.data.network.ServerBaseUrlProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServerConfigurationModule {

    @Binds
    @Singleton
    abstract fun bindServerConfigurationRepository(
        repository: DefaultServerConfigurationRepository,
    ): ServerConfigurationRepository

    @Binds
    abstract fun bindServerBaseUrlProvider(
        provider: SavedServerBaseUrlProvider,
    ): ServerBaseUrlProvider
}
