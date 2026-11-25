package com.omnibus.omnibus.data.database.di

import android.content.Context
import androidx.room.Room
import com.omnibus.omnibus.data.audiobook.crossformat.local.DismissedResumeOfferDao
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionDao
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateDao
import com.omnibus.omnibus.data.database.OmnibusDatabase
import com.omnibus.omnibus.data.libraries.local.BookDao
import com.omnibus.omnibus.data.libraries.local.LibrariesDao
import com.omnibus.omnibus.data.progress.local.ReadingPositionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the app's [OmnibusDatabase] and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // build() doesn't open the file, so injecting the database does no I/O. No destructive migration.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OmnibusDatabase =
        Room.databaseBuilder(context, OmnibusDatabase::class.java, OmnibusDatabase.NAME).build()

    @Provides
    fun provideLibrariesDao(database: OmnibusDatabase): LibrariesDao = database.librariesDao()

    @Provides
    fun provideBookDao(database: OmnibusDatabase): BookDao = database.bookDao()

    @Provides
    fun provideReadingPositionDao(database: OmnibusDatabase): ReadingPositionDao = database.readingPositionDao()

    @Provides
    fun provideListeningPositionDao(database: OmnibusDatabase): ListeningPositionDao = database.listeningPositionDao()

    @Provides
    fun providePlaybackRateDao(database: OmnibusDatabase): PlaybackRateDao = database.playbackRateDao()

    @Provides
    fun provideDismissedResumeOfferDao(database: OmnibusDatabase): DismissedResumeOfferDao =
        database.dismissedResumeOfferDao()
}
