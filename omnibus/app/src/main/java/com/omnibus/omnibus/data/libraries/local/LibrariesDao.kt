package com.omnibus.omnibus.data.libraries.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/** The `libraries` table. Rows are never replaced, because a replace would cascade away the library's data. */
@Dao
abstract class LibrariesDao {

    /** Every library, in the order they were added. */
    @Query("SELECT * FROM libraries ORDER BY added_at, id")
    abstract fun librariesFlow(): Flow<List<LibraryEntity>>

    /** The active library, or null when none is active. */
    @Query("SELECT * FROM libraries WHERE is_active = 1 LIMIT 1")
    abstract fun activeLibraryFlow(): Flow<LibraryEntity?>

    @Query("SELECT * FROM libraries WHERE is_active = 1 LIMIT 1")
    abstract suspend fun activeLibrary(): LibraryEntity?

    /** The id of the active library, or null when none is active. */
    @Query("SELECT id FROM libraries WHERE is_active = 1 LIMIT 1")
    abstract suspend fun activeId(): String?

    @Query("SELECT COUNT(*) FROM libraries")
    abstract suspend fun count(): Int

    @Insert
    abstract suspend fun insert(library: LibraryEntity)

    @Query("UPDATE libraries SET is_active = 0")
    abstract suspend fun deactivateAll()

    /** Adds [library] as the only active library. */
    @Transaction
    open suspend fun insertActive(library: LibraryEntity) {
        deactivateAll()
        insert(library.copy(isActive = true))
    }

    /**
     * Deletes library [id]. Its books and their per-book rows go with it, through the foreign keys. Returns the
     * number of rows deleted.
     */
    @Query("DELETE FROM libraries WHERE id = :id")
    abstract suspend fun delete(id: String): Int

    // #366
    /** Returns the number of rows updated. */
    @Query("UPDATE libraries SET username = :username WHERE id = :id")
    abstract suspend fun setUsername(id: String, username: String): Int

    // #368
    /** Sets library [id]'s name. Returns the number of rows updated. */
    @Query("UPDATE libraries SET name = :name WHERE id = :id")
    abstract suspend fun rename(id: String, name: String): Int

    // #369
    /** Makes [id] the only active library and stamps it with [now]. Changes nothing, and returns 0, when [id] is unknown. */
    @Query(
        "UPDATE libraries SET is_active = (id = :id), " +
            "last_used_at = CASE WHEN id = :id THEN :now ELSE last_used_at END " +
            "WHERE EXISTS (SELECT 1 FROM libraries WHERE id = :id)"
    )
    abstract suspend fun setActive(id: String, now: Long): Int
}
