package com.omnibus.omnibus.data.audiobook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.ResumeStartResolver
import com.omnibus.omnibus.data.audiobook.position.DefaultListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.DefaultListeningProgressRepository
import com.omnibus.omnibus.data.audiobook.position.DefaultResumeStartResolver
import com.omnibus.omnibus.data.audiobook.position.DeviceTimeZone
import com.omnibus.omnibus.data.audiobook.position.ListeningPositionSync
import com.omnibus.omnibus.data.audiobook.position.ListeningProgressRepository
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionTracker
import com.omnibus.omnibus.data.audiobook.position.ReadStatusMarker
import com.omnibus.omnibus.data.audiobook.position.SystemDeviceTimeZone
import com.omnibus.omnibus.data.audiobook.position.local.AudioPositionStore
import com.omnibus.omnibus.data.audiobook.position.local.DefaultAudioPositionStore
import com.omnibus.omnibus.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

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

    companion object {
        // A file of its own: two DataStores must never share one file.
        private const val DATASTORE_FILE_NAME = "audiobook_positions.preferences_pb"

        @Provides
        @Singleton
        @ListeningPositionPreferences
        fun provideListeningPositionPreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
            )
    }
}

/** Marks the audio positions' DataStore, to keep it apart from the reading positions' DataStore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ListeningPositionPreferences
