package com.omnibus.omnibus.data.scan.di

import com.omnibus.omnibus.data.scan.DefaultScanRepository
import com.omnibus.omnibus.data.scan.ScanRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ScanModule {

    @Binds
    @Singleton
    abstract fun bindScanRepository(repository: DefaultScanRepository): ScanRepository
}
