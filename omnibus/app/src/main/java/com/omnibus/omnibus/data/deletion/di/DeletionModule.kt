package com.omnibus.omnibus.data.deletion.di

import com.omnibus.omnibus.data.deletion.DefaultDeletionRepository
import com.omnibus.omnibus.data.deletion.DeletionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeletionModule {

    @Binds
    @Singleton
    abstract fun bindDeletionRepository(repository: DefaultDeletionRepository): DeletionRepository
}
