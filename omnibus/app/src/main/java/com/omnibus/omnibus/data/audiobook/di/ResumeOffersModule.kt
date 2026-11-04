package com.omnibus.omnibus.data.audiobook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.DefaultCrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.local.CrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.crossformat.local.DefaultCrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.offers.DefaultResumeOffers
import com.omnibus.omnibus.data.audiobook.offers.ResumeOffers
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.di.IoDispatcher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

/**
 * Binds the resume offers (#166): the ebook-to-audio read and its dismissal memory, in a DataStore file of their own,
 * and the offers holder. The holder is also a [PlaybackListener] in the controller's set (C4), and the two bindings
 * share one instance because the class is `@Singleton`. Like [ListeningPositionModule], nothing here injects the
 * controller directly, so the graph stays acyclic.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ResumeOffersModule {

    @Binds
    @Singleton
    abstract fun bindResumeOffers(offers: DefaultResumeOffers): ResumeOffers

    @Binds
    @IntoSet
    abstract fun bindResumeOffersListener(offers: DefaultResumeOffers): PlaybackListener

    @Binds
    @Singleton
    abstract fun bindCrossFormatRepository(repository: DefaultCrossFormatRepository): CrossFormatRepository

    @Binds
    @Singleton
    abstract fun bindCrossFormatDismissStore(store: DefaultCrossFormatDismissStore): CrossFormatDismissStore

    companion object {
        // A file of its own: two DataStores must never share one file.
        private const val DATASTORE_FILE_NAME = "audiobook_resume_offers.preferences_pb"

        @Provides
        @Singleton
        @ResumeOffersPreferences
        fun provideResumeOffersPreferences(
            @ApplicationContext context: Context,
            @IoDispatcher ioDispatcher: CoroutineDispatcher,
        ): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + ioDispatcher),
                produceFile = { context.dataStoreFile(DATASTORE_FILE_NAME) },
            )
    }
}

/** Marks the resume offers' DataStore, to keep it apart from the audio positions' DataStore. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ResumeOffersPreferences
