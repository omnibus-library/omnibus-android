package com.omnibus.omnibus.data.library.di

import com.omnibus.omnibus.data.library.DefaultLibraryRepository
import com.omnibus.omnibus.data.library.LibraryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(
        repository: DefaultLibraryRepository,
    ): LibraryRepository
}
