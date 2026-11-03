package com.omnibus.omnibus.data.merge.di

import com.omnibus.omnibus.data.merge.DefaultMergeRepository
import com.omnibus.omnibus.data.merge.MergeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the merge repository for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class MergeModule {

    @Binds
    @Singleton
    abstract fun bindMergeRepository(
        repository: DefaultMergeRepository,
    ): MergeRepository
}
