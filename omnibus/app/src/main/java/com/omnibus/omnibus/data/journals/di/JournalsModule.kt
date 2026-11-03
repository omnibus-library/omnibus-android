package com.omnibus.omnibus.data.journals.di

import com.omnibus.omnibus.data.journals.DefaultJournalsRepository
import com.omnibus.omnibus.data.journals.JournalsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds [DefaultJournalsRepository] as the app-wide [JournalsRepository]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class JournalsModule {

    @Binds
    @Singleton
    abstract fun bindJournalsRepository(
        repository: DefaultJournalsRepository,
    ): JournalsRepository
}
