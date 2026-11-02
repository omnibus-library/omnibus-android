package com.omnibus.omnibus.data.readstatus.di

import com.omnibus.omnibus.data.readstatus.DefaultReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReadStatusModule {

    @Binds
    @Singleton
    abstract fun bindReadStatusRepository(
        repository: DefaultReadStatusRepository,
    ): ReadStatusRepository
}
