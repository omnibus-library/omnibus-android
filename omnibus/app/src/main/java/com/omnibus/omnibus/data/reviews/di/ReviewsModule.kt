package com.omnibus.omnibus.data.reviews.di

import com.omnibus.omnibus.data.libraries.LibrarySyncParticipant
import com.omnibus.omnibus.data.reviews.DefaultReviewsRepository
import com.omnibus.omnibus.data.reviews.ReviewsRepository
import com.omnibus.omnibus.data.reviews.ReviewsSyncParticipant
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ReviewsModule {

    @Binds
    @Singleton
    abstract fun bindReviewsRepository(
        repository: DefaultReviewsRepository,
    ): ReviewsRepository

    @Binds
    @IntoSet
    abstract fun bindReviewsSyncParticipant(participant: ReviewsSyncParticipant): LibrarySyncParticipant
}
