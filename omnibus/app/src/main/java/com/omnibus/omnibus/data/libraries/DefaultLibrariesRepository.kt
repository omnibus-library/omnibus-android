package com.omnibus.omnibus.data.libraries

import android.database.SQLException
import android.util.Log
import com.omnibus.omnibus.data.configuration.ServerAddress
import com.omnibus.omnibus.data.libraries.local.LibrariesDao
import com.omnibus.omnibus.data.libraries.local.LibraryEntity
import com.omnibus.omnibus.data.libraries.local.toLibrary
import com.omnibus.omnibus.data.libraries.upgrade.StorageUpgrade
import com.omnibus.omnibus.di.IoDispatcher
import com.omnibus.omnibus.di.TimeSource
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [LibrariesRepository] over the `libraries` table. Every call waits for the storage upgrade, so the libraries
 * of an upgraded install are there before anything reads them. While the upgrade hasn't finished, reads are absent
 * and writes return null, even if some of its rows were already committed.
 */
@Singleton
class DefaultLibrariesRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val dao: LibrariesDao,
    private val activeLibrary: DefaultActiveLibrary,
    private val upgrade: StorageUpgrade,
    private val clock: TimeSource,
) : LibrariesRepository {

    override fun getLibrariesFlow(): Flow<List<Library>> = flow {
        if (!upgrade.ensureDone()) {
            emit(emptyList())
            return@flow
        }
        emitAll(dao.librariesFlow().map { rows -> rows.map(LibraryEntity::toLibrary) })
    }.catch { error ->
        if (error is SQLException) {
            Log.e(TAG, "Unable to read the libraries", error)
            emit(emptyList())
        } else {
            throw error
        }
    }

    override fun getActiveLibraryFlow(): Flow<Library?> = flow {
        if (!upgrade.ensureDone()) {
            emit(null)
            return@flow
        }
        emitAll(dao.activeLibraryFlow().map { row -> row?.toLibrary() })
    }.catch { error ->
        if (error is SQLException) {
            Log.e(TAG, "Unable to read the active library", error)
            emit(null)
        } else {
            throw error
        }
    }.distinctUntilChanged()

    override suspend fun add(address: String, activate: Boolean): Library? = withContext(ioDispatcher) {
        if (!upgrade.ensureDone()) return@withContext null
        val normalized = ServerAddress.normalize(address) ?: address.trim()
        val now = clock.nowMillis()
        val entity = LibraryEntity(
            id = UUID.randomUUID().toString(),
            address = normalized,
            name = LibraryHost.of(normalized),
            username = null,
            addedAt = now,
            lastUsedAt = now,
            isActive = activate,
        )
        try {
            if (activate) dao.insertActive(entity) else dao.insert(entity)
        } catch (error: SQLException) {
            Log.e(TAG, "Unable to add the library", error)
            return@withContext null
        }
        if (activate) activeLibrary.update(entity.id)
        entity.toLibrary()
    }

    override suspend fun changeAddress(libraryId: String, address: String) {
        withContext(ioDispatcher) {
            if (!upgrade.ensureDone()) return@withContext
            val normalized = ServerAddress.normalize(address) ?: address.trim()
            try {
                dao.updateAddress(libraryId, normalized, LibraryHost.of(normalized))
            } catch (error: SQLException) {
                Log.e(TAG, "Unable to change the library's address", error)
            }
        }
    }

    private companion object {
        const val TAG = "LibrariesRepository"
    }
}
