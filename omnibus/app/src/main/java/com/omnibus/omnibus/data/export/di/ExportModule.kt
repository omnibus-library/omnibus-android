package com.omnibus.omnibus.data.export.di

import com.omnibus.omnibus.data.export.DefaultExportRepository
import com.omnibus.omnibus.data.export.ExportRepository
import com.omnibus.omnibus.data.export.ExportTimings
import com.omnibus.omnibus.data.export.local.DefaultExportFileStore
import com.omnibus.omnibus.data.export.local.ExportFileStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the export repository and its file store, and provides the production pacing. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {

    @Binds
    @Singleton
    abstract fun bindExportRepository(repository: DefaultExportRepository): ExportRepository

    @Binds
    abstract fun bindExportFileStore(store: DefaultExportFileStore): ExportFileStore

    companion object {
        @Provides
        fun provideExportTimings(): ExportTimings = ExportTimings()
    }
}
