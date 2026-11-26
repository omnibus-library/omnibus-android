package com.omnibus.omnibus.data.libraries.di

import com.omnibus.omnibus.data.libraries.DefaultLibraryRemoval
import com.omnibus.omnibus.data.libraries.InactiveLibraryRows
import com.omnibus.omnibus.data.libraries.LibraryRemoval
import com.omnibus.omnibus.data.libraries.RoomInactiveLibraryRows
import com.omnibus.omnibus.data.libraries.remote.DefaultInactiveLibraryApi
import com.omnibus.omnibus.data.libraries.remote.InactiveLibraryApi
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryRemovalModule {

    @Binds
    abstract fun bindInactiveLibraryApi(api: DefaultInactiveLibraryApi): InactiveLibraryApi

    @Binds
    abstract fun bindInactiveLibraryRows(rows: RoomInactiveLibraryRows): InactiveLibraryRows

    @Binds
    abstract fun bindLibraryRemoval(removal: DefaultLibraryRemoval): LibraryRemoval
}
