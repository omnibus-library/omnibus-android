package com.omnibus.omnibus.data.libraries.di

import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.DefaultActiveLibrary
import com.omnibus.omnibus.data.libraries.DefaultLibrariesRepository
import com.omnibus.omnibus.data.libraries.DefaultLibrarySwitch
import com.omnibus.omnibus.data.libraries.DefaultLibrarySync
import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.LibrarySwitch
import com.omnibus.omnibus.data.libraries.LibrarySync
import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import com.omnibus.omnibus.data.libraries.local.DefaultLibraryTokenStore
import com.omnibus.omnibus.data.libraries.local.DefaultPendingOpeningStore
import com.omnibus.omnibus.data.libraries.local.LegacyTokenAdopter
import com.omnibus.omnibus.data.libraries.local.LibraryTokenStore
import com.omnibus.omnibus.data.libraries.local.PendingOpeningStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibrariesModule {

    @Binds
    @Singleton
    abstract fun bindLibrariesRepository(repository: DefaultLibrariesRepository): LibrariesRepository

    @Binds
    abstract fun bindActiveLibrary(active: DefaultActiveLibrary): ActiveLibrary

    @Binds
    abstract fun bindLibraryTokenStore(store: DefaultLibraryTokenStore): LibraryTokenStore

    @Binds
    abstract fun bindLegacyTokenAdopter(store: DefaultLibraryTokenStore): LegacyTokenAdopter

    // #369
    @Multibinds
    abstract fun syncParticipants(): Set<LibrarySyncParticipant>

    @Binds
    @Singleton
    abstract fun bindLibrarySync(sync: DefaultLibrarySync): LibrarySync

    @Binds
    @Singleton
    abstract fun bindLibrarySwitch(switch: DefaultLibrarySwitch): LibrarySwitch

    @Binds
    @Singleton
    abstract fun bindPendingOpeningStore(store: DefaultPendingOpeningStore): PendingOpeningStore
}
