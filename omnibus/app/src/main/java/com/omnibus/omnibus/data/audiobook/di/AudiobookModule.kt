package com.omnibus.omnibus.data.audiobook.di

import com.omnibus.omnibus.data.audiobook.AudiobookRepository
import com.omnibus.omnibus.data.audiobook.DefaultAudiobookRepository
import com.omnibus.omnibus.data.audiobook.playback.AppBackgroundSignal
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.AudioEngine
import com.omnibus.omnibus.data.audiobook.playback.CoverArtworkSource
import com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.DefaultCoverArtworkSource
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.PlaybackRateStore
import com.omnibus.omnibus.data.audiobook.playback.ProcessLifecycleAppBackgroundSignal
import com.omnibus.omnibus.data.audiobook.playback.ResumeStartResolver
import com.omnibus.omnibus.data.audiobook.playback.SleepTimer
import com.omnibus.omnibus.data.audiobook.playback.media3.Media3AudioEngine
import com.omnibus.omnibus.data.audiobook.remote.DefaultRecentListeningSource
import com.omnibus.omnibus.data.audiobook.remote.RecentListeningSource
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Binds the audiobook manifest, the playback controller and its engine, and declares the seams that later tickets bind
 * into. With none of the seams bound the app builds and plays a book (R19).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudiobookModule {

    @Binds
    @Singleton
    abstract fun bindAudiobookRepository(repository: DefaultAudiobookRepository): AudiobookRepository

    @Binds
    @Singleton
    abstract fun bindAudiobookController(controller: DefaultAudiobookController): AudiobookController

    @Binds
    @Singleton
    abstract fun bindRecentListeningSource(source: DefaultRecentListeningSource): RecentListeningSource

    @Binds
    @Singleton
    abstract fun bindAudioEngine(engine: Media3AudioEngine): AudioEngine

    @Binds
    @Singleton
    abstract fun bindAppBackgroundSignal(signal: ProcessLifecycleAppBackgroundSignal): AppBackgroundSignal

    @Binds
    abstract fun bindCoverArtworkSource(source: DefaultCoverArtworkSource): CoverArtworkSource

    @BindsOptionalOf
    abstract fun optionalResumeStartResolver(): ResumeStartResolver

    @BindsOptionalOf
    abstract fun optionalPlaybackRateStore(): PlaybackRateStore

    @BindsOptionalOf
    abstract fun optionalSleepTimer(): SleepTimer

    @Multibinds
    abstract fun playbackListeners(): Set<PlaybackListener>
}
