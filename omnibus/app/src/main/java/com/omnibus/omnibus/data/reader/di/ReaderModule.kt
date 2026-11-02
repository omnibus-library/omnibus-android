package com.omnibus.omnibus.data.reader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.reader.local.DefaultReaderSettingsStore
import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.data.reader.local.ReaderSettingsStore
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.data.reader.remote.DefaultBookFileSource
import com.omnibus.omnibus.di.IoDispatcher
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
abstract class ReaderModule {

    @Binds
    @Singleton
    abstract fun bindBookFileSource(source: DefaultBookFileSource): BookFileSource

    @Binds
    @Singleton
    abstract fun bindReaderSettingsStore(store: DefaultReaderSettingsStore): ReaderSettingsStore

    companion object {
        /** Replaced by the offline-reading work, which knows where downloads live. */
        @Provides
        @Singleton
        fun provideLocalBookFiles(): LocalBookFiles = LocalBookFiles.None

        // A file of its own: two DataStores must never share one file.
        private const val DATASTORE_FILE_NAME = "reader_preferences.preferences_pb"

        @Provides
        @Singleton
        @ReaderPreferences
        fun provideReaderPreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
            )
    }
}

/** Marks the reader's settings DataStore, to keep it apart from other DataStores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ReaderPreferences
