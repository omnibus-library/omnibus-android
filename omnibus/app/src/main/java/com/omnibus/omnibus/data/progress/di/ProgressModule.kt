package com.omnibus.omnibus.data.progress.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.progress.DefaultProgressRepository
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.data.progress.local.DefaultReadingPositionStore
import com.omnibus.omnibus.data.progress.local.ReadingPositionStore
import com.omnibus.omnibus.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class ProgressModule {

    @Binds
    @Singleton
    abstract fun bindReadingPositionStore(
        store: DefaultReadingPositionStore,
    ): ReadingPositionStore

    @Binds
    @Singleton
    abstract fun bindProgressRepository(
        repository: DefaultProgressRepository,
    ): ProgressRepository

    companion object {
        // A file of its own: two DataStores must never share one file.
        private const val DATASTORE_FILE_NAME = "reading_positions.preferences_pb"

        @Provides
        @Singleton
        @ReadingPositionPreferences
        fun provideReadingPositionPreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
            )
    }
}

/** Marks the reading positions' DataStore, to keep it apart from other DataStores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReadingPositionPreferences
