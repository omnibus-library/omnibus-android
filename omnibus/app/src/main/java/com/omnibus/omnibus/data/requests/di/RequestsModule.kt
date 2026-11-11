package com.omnibus.omnibus.data.requests.di

import com.omnibus.omnibus.data.requests.DefaultRequestsRepository
import com.omnibus.omnibus.data.requests.RequestsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RequestsModule {

    @Binds
    @Singleton
    abstract fun bindRequestsRepository(repository: DefaultRequestsRepository): RequestsRepository
}
