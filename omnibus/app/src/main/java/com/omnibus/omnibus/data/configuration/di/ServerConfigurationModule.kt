package com.omnibus.omnibus.data.configuration.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.auth.di.IoDispatcher
import com.omnibus.omnibus.data.configuration.DefaultServerConfigurationRepository
import com.omnibus.omnibus.data.configuration.ServerConfigurationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ServerConfigurationModule {

    @Binds
    @Singleton
    abstract fun bindServerConfigurationRepository(
        repository: DefaultServerConfigurationRepository,
    ): ServerConfigurationRepository

    companion object {
        private const val DATASTORE_FILE_NAME = "server_configuration.preferences_pb"

        @Provides
        @Singleton
        @ServerConfigPreferences
        fun provideServerConfigPreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
            )
    }
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerConfigPreferences
