package com.omnibus.omnibus.data.series.di

import com.omnibus.omnibus.data.series.DefaultSeriesRepository
import com.omnibus.omnibus.data.series.SeriesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the series repository for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SeriesModule {

    @Binds
    @Singleton
    abstract fun bindSeriesRepository(
        repository: DefaultSeriesRepository,
    ): SeriesRepository
}
