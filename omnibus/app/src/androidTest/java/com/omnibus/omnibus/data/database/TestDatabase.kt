package com.omnibus.omnibus.data.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.audiobook.position.ListeningSessionReport
import com.omnibus.omnibus.data.libraries.local.LibraryEntity
import java.util.UUID

/** Builds an [OmnibusDatabase] for an instrumented test. */
object TestDatabase {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** A database held in memory, gone once it is closed. */
    fun inMemory(): OmnibusDatabase =
        Room.inMemoryDatabaseBuilder(context, OmnibusDatabase::class.java).build()

    /** A database file named [name], for tests that close it and reopen it. */
    fun onDisk(name: String): OmnibusDatabase =
        Room.databaseBuilder(context, OmnibusDatabase::class.java, name).build()
}

/** The per-book tables, all of which reference `books` by `book_id`. */
val PER_BOOK_TABLES = listOf(
    "reading_positions",
    "listening_positions",
    "listening_sessions",
    "playback_rates",
    "dismissed_resume_offers",
)

/** Inserts a library through its DAO and returns its id. Every library a test writes under must exist first. */
suspend fun OmnibusDatabase.insertLibrary(
    id: String = UUID.randomUUID().toString(),
    active: Boolean = false,
): String {
    val library = LibraryEntity(
        id = id,
        address = "https://$id.example",
        name = id,
        username = null,
        addedAt = 0L,
        lastUsedAt = 0L,
        isActive = active,
    )
    if (active) librariesDao().insertActive(library) else librariesDao().insert(library)
    return id
}

/** A queued session for [bookUuid]. Client ids are a global primary key, so each call gets a fresh one by default. */
fun listeningSessionReport(
    bookUuid: String,
    clientId: String = UUID.randomUUID().toString(),
) = ListeningSessionReport(
    bookUuid = bookUuid,
    startedAt = 1_000L,
    endedAt = 2_000L,
    progressUnits = 60L,
    clientId = clientId,
    utcOffsetMinutes = 0,
    timeZone = "UTC",
)

/** Writes one row to each per-book table for [bookUuid] in [libraryId]. */
suspend fun OmnibusDatabase.seedAllPerBookRows(libraryId: String, bookUuid: String) {
    readingPositionDao().save(libraryId, bookUuid, cfi = "epubcfi(/6/2)", percent = 10, clientUpdatedAt = 1L)
    listeningPositionDao().save(libraryId, bookUuid, seconds = 30.0, fileId = 2L, clientUpdatedAt = 3L)
    listeningPositionDao().queueSessions(libraryId, listOf(listeningSessionReport(bookUuid)))
    playbackRateDao().saveLocal(libraryId, bookUuid, rate = 1.5)
    dismissedResumeOfferDao().store(libraryId, bookUuid, sourceClock = 7L)
}

/** The number of rows of [table] whose book is in [libraryId]. [table] is `books` or a per-book table. */
fun OmnibusDatabase.rowsInLibrary(table: String, libraryId: String): Int =
    if (table == "books") {
        countWith("SELECT COUNT(*) FROM books WHERE library_id = ?", libraryId)
    } else {
        countWith(
            "SELECT COUNT(*) FROM $table t JOIN books b ON b.id = t.book_id WHERE b.library_id = ?",
            libraryId,
        )
    }

/** The number of rows of [table] whose book has [bookUuid], in any library. [table] is `books` or a per-book table. */
fun OmnibusDatabase.rowsOfBook(table: String, bookUuid: String): Int =
    if (table == "books") {
        countWith("SELECT COUNT(*) FROM books WHERE book_uuid = ?", bookUuid)
    } else {
        countWith(
            "SELECT COUNT(*) FROM $table t JOIN books b ON b.id = t.book_id WHERE b.book_uuid = ?",
            bookUuid,
        )
    }

private fun OmnibusDatabase.countWith(sql: String, arg: String): Int =
    openHelper.readableDatabase.query(sql, arrayOf<Any>(arg)).use { cursor ->
        cursor.moveToFirst()
        cursor.getInt(0)
    }
