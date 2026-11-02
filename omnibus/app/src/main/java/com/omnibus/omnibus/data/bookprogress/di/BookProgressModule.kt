package com.omnibus.omnibus.data.bookprogress.di

import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.bookprogress.DefaultBookProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BookProgressModule {

    @Binds
    @Singleton
    abstract fun bindBookProgressRepository(
        repository: DefaultBookProgressRepository,
    ): BookProgressRepository
}
