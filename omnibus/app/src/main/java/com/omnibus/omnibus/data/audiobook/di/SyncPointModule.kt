package com.omnibus.omnibus.data.audiobook.di

import com.omnibus.omnibus.data.audiobook.syncpoint.DefaultSyncPointRepository
import com.omnibus.omnibus.data.audiobook.syncpoint.SyncPointRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [DefaultSyncPointRepository] as the app-wide [SyncPointRepository] for Sync here (#171). */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncPointModule {

    @Binds
    @Singleton
    abstract fun bindSyncPointRepository(repository: DefaultSyncPointRepository): SyncPointRepository
}
