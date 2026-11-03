package com.omnibus.omnibus.data.suggestions.di

import com.omnibus.omnibus.data.suggestions.DefaultSuggestionsRepository
import com.omnibus.omnibus.data.suggestions.SuggestionsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the suggestions repository for the whole app. */
@Module
@InstallIn(SingletonComponent::class)
abstract class SuggestionsModule {

    @Binds
    @Singleton
    abstract fun bindSuggestionsRepository(
        repository: DefaultSuggestionsRepository,
    ): SuggestionsRepository
}
