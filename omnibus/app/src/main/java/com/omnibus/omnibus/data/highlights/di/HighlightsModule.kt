package com.omnibus.omnibus.data.highlights.di

import com.omnibus.omnibus.data.highlights.DefaultHighlightsRepository
import com.omnibus.omnibus.data.highlights.HighlightsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [DefaultHighlightsRepository] as the app-wide [HighlightsRepository]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HighlightsModule {

    @Binds
    @Singleton
    abstract fun bindHighlightsRepository(
        repository: DefaultHighlightsRepository,
    ): HighlightsRepository
}
