package com.omnibus.omnibus.data.library.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.library.DefaultLibraryRepository
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.local.DefaultLibrarySortPreferenceStore
import com.omnibus.omnibus.data.library.local.LibrarySortPreferenceStore
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
abstract class LibraryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        repository: DefaultLibraryRepository,
    ): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindLibrarySortPreferenceStore(
        store: DefaultLibrarySortPreferenceStore,
    ): LibrarySortPreferenceStore

    companion object {
        // A file of its own: two DataStores must never share one file.
        private const val DATASTORE_FILE_NAME = "library_preferences.preferences_pb"

        @Provides
        @Singleton
        @LibraryPreferences
        fun provideLibraryPreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
            )
    }
}

/** Marks the Library's sort-preference DataStore, to keep it apart from other DataStores. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LibraryPreferences
