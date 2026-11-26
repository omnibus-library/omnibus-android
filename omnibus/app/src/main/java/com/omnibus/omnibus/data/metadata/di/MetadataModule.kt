package com.omnibus.omnibus.data.metadata.di

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import com.omnibus.omnibus.data.metadata.DefaultMetadataRepository
import com.omnibus.omnibus.data.metadata.MetadataRepository
import com.omnibus.omnibus.data.metadata.MetadataSyncParticipant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/** Binds the metadata repository for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MetadataModule {

    @Binds
    @Singleton
    abstract fun bindMetadataRepository(
        repository: DefaultMetadataRepository,
    ): MetadataRepository

    @Binds
    @IntoSet
    abstract fun bindMetadataSyncParticipant(participant: MetadataSyncParticipant): LibrarySyncParticipant
}
