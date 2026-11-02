package com.omnibus.omnibus.data.shelves.di

import com.omnibus.omnibus.data.shelves.DefaultShelvesRepository
import com.omnibus.omnibus.data.shelves.ShelvesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the shelves repository for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShelvesModule {

    @Binds
    @Singleton
    abstract fun bindShelvesRepository(
        repository: DefaultShelvesRepository,
    ): ShelvesRepository
}
