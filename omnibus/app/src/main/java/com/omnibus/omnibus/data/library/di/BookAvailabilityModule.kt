package com.omnibus.omnibus.data.library.di

import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.DefaultBookAvailability
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Binds the open guard. [com.omnibus.omnibus.data.reader.local.LocalBookFiles] is bound by the reader module. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BookAvailabilityModule {

    @Binds
    abstract fun bindBookAvailability(availability: DefaultBookAvailability): BookAvailability
}
