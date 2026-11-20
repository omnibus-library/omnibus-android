package com.omnibus.omnibus.data.search.di

import com.omnibus.omnibus.data.search.DefaultLibrarySearchRepository
import com.omnibus.omnibus.data.search.LibrarySearchRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibrarySearchModule {

    @Binds
    @Singleton
    abstract fun bindLibrarySearchRepository(repository: DefaultLibrarySearchRepository): LibrarySearchRepository
}
