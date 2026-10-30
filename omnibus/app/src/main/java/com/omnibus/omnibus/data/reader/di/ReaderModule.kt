package com.omnibus.omnibus.data.reader.di

import com.omnibus.omnibus.data.reader.local.LocalBookFiles
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.data.reader.remote.DefaultBookFileSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReaderModule {

    @Binds
    @Singleton
    abstract fun bindBookFileSource(source: DefaultBookFileSource): BookFileSource

    companion object {
        /** Replaced by the offline-reading work, which knows where downloads live. */
        @Provides
        @Singleton
        fun provideLocalBookFiles(): LocalBookFiles = LocalBookFiles.None
    }
}
