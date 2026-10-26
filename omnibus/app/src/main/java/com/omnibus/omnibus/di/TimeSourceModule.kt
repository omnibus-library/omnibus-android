package com.omnibus.omnibus.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** The current wall-clock time, injected so time-dependent logic can be tested. */
fun interface TimeSource {
    /** Milliseconds since the Unix epoch. */
    fun nowMillis(): Long
}

/** The device clock. */
class SystemTimeSource @Inject constructor() : TimeSource {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/** Binds the device clock as the app-wide [TimeSource]. */
@Module
@InstallIn(SingletonComponent::class)
abstract class TimeSourceModule {

    @Binds
    abstract fun bindTimeSource(timeSource: SystemTimeSource): TimeSource
}
