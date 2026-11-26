package com.omnibus.omnibus.data.readstatus.di

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import com.omnibus.omnibus.data.readstatus.DefaultReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusSyncParticipant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadStatusModule {

    @Binds
    @Singleton
    abstract fun bindReadStatusRepository(
        repository: DefaultReadStatusRepository,
    ): ReadStatusRepository

    @Binds
    @IntoSet
    abstract fun bindReadStatusSyncParticipant(participant: ReadStatusSyncParticipant): LibrarySyncParticipant
}
