package com.omnibus.omnibus.data.quotecard.di

import com.omnibus.omnibus.data.quotecard.DefaultQuoteCardRepository
import com.omnibus.omnibus.data.quotecard.QuoteCardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [DefaultQuoteCardRepository] as the app-wide [QuoteCardRepository]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class QuoteCardModule {

    @Binds
    @Singleton
    abstract fun bindQuoteCardRepository(repository: DefaultQuoteCardRepository): QuoteCardRepository
}
