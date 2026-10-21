package com.omnibus.omnibus.data.libraryconfiguration.di

import com.omnibus.omnibus.data.libraryconfiguration.DefaultLibraryConfigurationRepository
import com.omnibus.omnibus.data.libraryconfiguration.LibraryConfigurationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryConfigurationModule {

    @Binds
    @Singleton
    abstract fun bindLibraryConfigurationRepository(
        repository: DefaultLibraryConfigurationRepository,
    ): LibraryConfigurationRepository
}
