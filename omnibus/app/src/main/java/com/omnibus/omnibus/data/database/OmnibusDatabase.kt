package com.omnibus.omnibus.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.omnibus.omnibus.data.audiobook.crossformat.local.DismissedResumeOfferDao
import com.omnibus.omnibus.data.audiobook.crossformat.local.DismissedResumeOfferEntity
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionDao
import com.omnibus.omnibus.data.audiobook.position.local.ListeningPositionEntity
import com.omnibus.omnibus.data.audiobook.position.local.ListeningSessionEntity
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateDao
import com.omnibus.omnibus.data.audiobook.rate.local.PlaybackRateEntity
import com.omnibus.omnibus.data.libraries.local.BookDao
import com.omnibus.omnibus.data.libraries.local.BookEntity
import com.omnibus.omnibus.data.libraries.local.LibrariesDao
import com.omnibus.omnibus.data.libraries.local.LibraryEntity
import com.omnibus.omnibus.data.progress.local.ReadingPositionDao
import com.omnibus.omnibus.data.progress.local.ReadingPositionEntity

/**
 * The app's Room database, `omnibus.db`. Schema version 1 is final for the whole epic. There is no
 * destructive migration: a version without a migration fails to open rather than dropping data.
 */
@Database(
    entities = [
        LibraryEntity::class,
        BookEntity::class,
        ReadingPositionEntity::class,
        ListeningPositionEntity::class,
        ListeningSessionEntity::class,
        PlaybackRateEntity::class,
        DismissedResumeOfferEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class OmnibusDatabase : RoomDatabase() {

    abstract fun librariesDao(): LibrariesDao

    abstract fun bookDao(): BookDao

    abstract fun readingPositionDao(): ReadingPositionDao

    abstract fun listeningPositionDao(): ListeningPositionDao

    abstract fun playbackRateDao(): PlaybackRateDao

    abstract fun dismissedResumeOfferDao(): DismissedResumeOfferDao

    companion object {
        const val NAME = "omnibus.db"
    }
}
