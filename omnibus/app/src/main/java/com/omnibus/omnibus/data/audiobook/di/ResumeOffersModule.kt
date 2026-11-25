package com.omnibus.omnibus.data.audiobook.di

import com.omnibus.omnibus.data.audiobook.crossformat.CrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.DefaultCrossFormatRepository
import com.omnibus.omnibus.data.audiobook.crossformat.local.CrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.crossformat.local.DefaultCrossFormatDismissStore
import com.omnibus.omnibus.data.audiobook.offers.DefaultResumeOffers
import com.omnibus.omnibus.data.audiobook.offers.ResumeOffers
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Binds the resume offers (#166): the ebook-to-audio read and its dismissal memory,
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
}
