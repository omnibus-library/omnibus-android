package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.configuration.ServerAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * In-memory [LibrariesRepository] for tests. It holds [initial] libraries, and every flow is a view of them.
 */
class TestLibrariesRepository(initial: List<Library> = emptyList()) : LibrariesRepository {

    /** The libraries, in the order they were added. */
    val libraries = MutableStateFlow(initial)

    /** When true, [add] returns null and adds nothing. */
    var addFailure = false

    private var addedCount = 0L

    override fun getLibrariesFlow(): Flow<List<Library>> = libraries

    override fun getActiveLibraryFlow(): Flow<Library?> =
        libraries.map { list -> list.firstOrNull { it.isActive } }.distinctUntilChanged()

    override suspend fun add(address: String, activate: Boolean): Library? {
        if (addFailure) return null
        val normalized = ServerAddress.normalize(address) ?: address.trim()
        val addedAt = addedCount++
        val library = Library(
            id = UUID.randomUUID().toString(),
            address = normalized,
            name = LibraryHost.of(normalized),
            username = null,
            addedAt = addedAt,
            lastUsedAt = addedAt,
            isActive = activate,
        )
        libraries.update { current ->
            val kept = if (activate) current.map { it.copy(isActive = false) } else current
            kept + library
        }
        return library
    }

    override suspend fun changeAddress(libraryId: String, address: String) {
        val normalized = ServerAddress.normalize(address) ?: address.trim()
        libraries.update { list ->
            list.map { library ->
                if (library.id == libraryId) {
                    library.copy(address = normalized, name = LibraryHost.of(normalized))
                } else {
                    library
                }
            }
        }
    }
}
