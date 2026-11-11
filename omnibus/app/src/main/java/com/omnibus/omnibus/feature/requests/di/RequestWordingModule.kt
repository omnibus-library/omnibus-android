package com.omnibus.omnibus.feature.requests.di

import android.content.Context
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.requestWording
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** Where a ViewModel gets the request wording, so it can build a message without a [Context]. */
fun interface RequestWordingSource {
    /** The wording for the device's current locale. */
    fun wording(): RequestWording
}

/** Reads the wording from the app's string resources each time, so a locale change is picked up. */
@Module
@InstallIn(SingletonComponent::class)
object RequestWordingModule {

    @Provides
    fun provideRequestWordingSource(@ApplicationContext context: Context): RequestWordingSource =
        RequestWordingSource { requestWording(context.resources) }
}
