package com.omnibus.omnibus.data.audiobook.di

import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.ResumeStartResolver
import com.omnibus.omnibus.data.audiobook.position.DefaultListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.DefaultListeningProgressRepository
import com.omnibus.omnibus.data.audiobook.position.DefaultResumeStartResolver
import com.omnibus.omnibus.data.audiobook.position.DeviceTimeZone
import com.omnibus.omnibus.data.audiobook.position.ListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.ListeningProgressRepository
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionTracker
import com.omnibus.omnibus.data.audiobook.position.ListeningSyncParticipant
import com.omnibus.omnibus.data.audiobook.position.ReadStatusMarker
import com.omnibus.omnibus.data.audiobook.position.SystemDeviceTimeZone
import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.local.DefaultAudioPositionStore
import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Binds the listening-position feature (#164). It fills the optional [ResumeStartResolver] seam that [AudiobookModule]
 * declares (C5), binds the C8 [ListeningPositionSync], and adds the three [PlaybackListener]s to the controller's set:
 * the position sync (saves), the session tracker (listening time) and the read-status marker. The three listeners are
 * `@Singleton`, so the set and the C8 binding share one instance each. Nothing here injects the controller directly:
 * the sync reaches it through `dagger.Lazy`, which keeps the graph acyclic.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ListeningPositionModule {

    @Binds
    abstract fun bindResumeStartResolver(resolver: DefaultResumeStartResolver): ResumeStartResolver

    @Binds
    @Singleton
    abstract fun bindListeningPositionSync(sync: DefaultListeningPositionSync): ListeningPositionSync

    @Binds
    @IntoSet
    abstract fun bindPositionListener(sync: DefaultListeningPositionSync): PlaybackListener

    @Binds
    @IntoSet
    abstract fun bindSessionListener(tracker: ListeningSessionTracker): PlaybackListener

    @Binds
    @IntoSet
    abstract fun bindReadStatusListener(marker: ReadStatusMarker): PlaybackListener

    @Binds
    @Singleton
    abstract fun bindListeningProgressRepository(
        repository: DefaultListeningProgressRepository,
    ): ListeningProgressRepository

    @Binds
    @Singleton
    abstract fun bindAudioPositionStore(store: DefaultAudioPositionStore): AudioPositionStore

    @Binds
    abstract fun bindDeviceTimeZone(zone: SystemDeviceTimeZone): DeviceTimeZone

    @Binds
    @IntoSet
    abstract fun bindListeningSyncParticipant(participant: ListeningSyncParticipant): LibrarySyncParticipant
}
