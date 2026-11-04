package com.omnibus.omnibus.data.audiobook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.audiobook.playback.PlaybackRateStore
import com.omnibus.omnibus.data.audiobook.rate.DefaultPlaybackRateStore
import com.omnibus.omnibus.data.audiobook.rate.local.DefaultPlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.remote.DefaultPlaybackRateApi
import com.omnibus.omnibus.data.audiobook.rate.remote.PlaybackRateApi
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

/**
 * Binds the playback speed store (#168) into the `@BindsOptionalOf` [PlaybackRateStore] seam of [AudiobookModule], with its
 * API and per-book cache, and the cache's DataStore in a file of its own. Nothing here injects the controller, so the
 * graph stays acyclic: the store is built from the API, the cache, the connectivity monitor and the application scope.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackRateModule {

    @Binds
    @Singleton
    abstract fun bindPlaybackRateStore(store: DefaultPlaybackRateStore): PlaybackRateStore

    @Binds
    abstract fun bindPlaybackRateApi(api: DefaultPlaybackRateApi): PlaybackRateApi

    @Binds
    abstract fun bindPlaybackRateCache(cache: DefaultPlaybackRateCache): PlaybackRateCache

    companion object {

        @Provides
        @Singleton
        @PlaybackRatePreferences
        fun providePlaybackRatePreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(PLAYBACK_RATE_DATASTORE_FILE) },
            )
    }
}

/** The file the per-book playback rates are kept in. A DataStore file must end in `.preferences_pb` (R14). */
internal const val PLAYBACK_RATE_DATASTORE_FILE = "audiobook_playback_rates.preferences_pb"

/** Marks the playback rates' DataStore, to keep it apart from the other audiobook DataStores (R14, R18). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlaybackRatePreferences
