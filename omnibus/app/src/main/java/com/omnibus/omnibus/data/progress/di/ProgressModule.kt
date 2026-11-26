package com.omnibus.omnibus.data.progress.di

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import com.omnibus.omnibus.data.progress.DefaultProgressRepository
import com.omnibus.omnibus.data.progress.ProgressRepository
import com.omnibus.omnibus.data.progress.ReadingPositionSyncParticipant
import com.omnibus.omnibus.data.progress.local.DefaultReadingPositionStore
import com.omnibus.omnibus.data.progress.local.ReadingPositionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

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

    @Binds
    @IntoSet
    abstract fun bindReadingPositionSyncParticipant(participant: ReadingPositionSyncParticipant): LibrarySyncParticipant
}
