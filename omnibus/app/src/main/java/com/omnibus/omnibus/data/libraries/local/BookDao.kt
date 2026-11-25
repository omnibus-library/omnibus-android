package com.omnibus.omnibus.data.libraries.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * The shared base of every DAO that writes per-book rows. [findOrCreate] finds or creates the `books`
 * row of a library and book, so each per-book write is one transaction that also creates the book row.
 */
abstract class BookRows {

    @Query("SELECT id FROM books WHERE library_id = :libraryId AND book_uuid = :bookUuid")
    abstract suspend fun bookId(libraryId: String, bookUuid: String): Long?

    // Plain insert, never REPLACE: a replaced books row would cascade away its per-book rows.
    @Insert
    abstract suspend fun insertBook(book: BookEntity): Long

    /** The id of the book's row in [libraryId], created when the library has no such book yet. */
    @Transaction
    open suspend fun findOrCreate(libraryId: String, bookUuid: String): Long =
        bookId(libraryId, bookUuid) ?: insertBook(BookEntity(libraryId = libraryId, bookUuid = bookUuid))
}

/** Direct access to the `books` table. The per-book DAOs extend [BookRows] instead. */
@Dao
abstract class BookDao : BookRows()
