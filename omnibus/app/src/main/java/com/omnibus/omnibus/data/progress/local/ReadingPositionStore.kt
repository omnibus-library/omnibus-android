package com.omnibus.omnibus.data.progress.local

import android.database.SQLException
import android.util.Log
import com.omnibus.omnibus.data.libraries.ActiveLibrary
import com.omnibus.omnibus.data.libraries.writeToActiveLibrary
import com.omnibus.omnibus.data.progress.ReadingPosition
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Per-device storage of the last reading position of each book. */
interface ReadingPositionStore {

    /** The stored position of [bookUuid]. Emits null when none is stored or the read fails. */
    fun getPositionFlow(bookUuid: String): Flow<ReadingPosition?>

    /** Stores [position] for its book, replacing the previous one. Failures are logged and not thrown. */
    suspend fun save(position: ReadingPosition)
}

/**
 * [ReadingPositionStore] backed by the `reading_positions` table, scoped to the active library. With no active library
 * reads are absent and writes are dropped.
 */
class DefaultReadingPositionStore @Inject constructor(
    private val dao: ReadingPositionDao,
    private val activeLibrary: ActiveLibrary,
) : ReadingPositionStore {

    override fun getPositionFlow(bookUuid: String): Flow<ReadingPosition?> = flow {
        val libraryId = activeLibrary.id()
        if (libraryId == null) {
            emit(null)
            return@flow
        }
        emitAll(dao.positionFlow(libraryId, bookUuid).map { row -> row?.toDomain(bookUuid) })
    }.catch { error ->
        if (error is SQLException) {
            Log.w(TAG, "Unable to read the reading position", error)
            emit(null)
        } else {
            throw error
        }
    }

    override suspend fun save(position: ReadingPosition) {
        activeLibrary.writeToActiveLibrary(TAG) { libraryId ->
            dao.save(libraryId, position.bookUuid, position.cfi, position.percent, position.clientUpdatedAt)
        }
    }

    private fun ReadingPositionEntity.toDomain(bookUuid: String) =
        ReadingPosition(bookUuid = bookUuid, cfi = cfi, percent = percent, clientUpdatedAt = clientUpdatedAt)

    private companion object {
        const val TAG = "ReadingPositionStore"
    }
}
