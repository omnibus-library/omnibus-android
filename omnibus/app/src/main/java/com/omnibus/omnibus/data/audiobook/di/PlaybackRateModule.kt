package com.omnibus.omnibus.data.audiobook.di

import com.omnibus.omnibus.data.audiobook.playback.PlaybackRateStore
import com.omnibus.omnibus.data.audiobook.rate.DefaultPlaybackRateStore
import com.omnibus.omnibus.data.audiobook.rate.PlaybackRateSyncParticipant
import com.omnibus.omnibus.data.audiobook.rate.local.DefaultPlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateCache
import com.omnibus.omnibus.data.audiobook.rate.remote.DefaultPlaybackRateApi
import com.omnibus.omnibus.data.audiobook.rate.remote.PlaybackRateApi
import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Binds the playback speed store (#168) into the `@BindsOptionalOf` [PlaybackRateStore] seam of [AudiobookModule], with its
 * API and per-book cache. Nothing here injects the controller, so the
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

    @Binds
    @IntoSet
    abstract fun bindPlaybackRateSyncParticipant(participant: PlaybackRateSyncParticipant): LibrarySyncParticipant
}
