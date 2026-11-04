package com.omnibus.omnibus.data.audiobook.di

import com.omnibus.omnibus.data.audiobook.playback.SleepTimer
import com.omnibus.omnibus.data.audiobook.sleep.DefaultSleepTimer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the sleep timer (#169) into the controller's optional seam (`AudiobookModule.optionalSleepTimer`, C7). The
 * controller takes the timer through [dagger.Lazy] inside it, so the binding adds no cycle.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SleepTimerModule {

    @Binds
    @Singleton
    abstract fun bindSleepTimer(timer: DefaultSleepTimer): SleepTimer
}
