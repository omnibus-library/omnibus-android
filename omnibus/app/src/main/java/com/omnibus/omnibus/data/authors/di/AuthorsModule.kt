package com.omnibus.omnibus.data.authors.di

import com.omnibus.omnibus.data.authors.AuthorsRepository
import com.omnibus.omnibus.data.authors.DefaultAuthorsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the authors repository for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthorsModule {

    @Binds
    @Singleton
    abstract fun bindAuthorsRepository(
        repository: DefaultAuthorsRepository,
    ): AuthorsRepository
}
