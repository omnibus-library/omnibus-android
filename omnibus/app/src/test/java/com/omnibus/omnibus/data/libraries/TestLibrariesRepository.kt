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

    /** The ids passed to [remove], in call order. */
    val removedIds = mutableListOf<String>()

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

    override suspend fun remove(libraryId: String) {
        removedIds += libraryId
        libraries.update { list -> list.filterNot { it.id == libraryId } }
    }

    // #366
    /** When non-null, [setUsername] throws this instead. */
    var setUsernameFailure: Throwable? = null

    /** The (libraryId, username) pairs passed to [setUsername], in call order. */
    val usernameUpdates = mutableListOf<Pair<String, String>>()

    override suspend fun setUsername(libraryId: String, username: String) {
        setUsernameFailure?.let { throw it }
        usernameUpdates += libraryId to username
        libraries.update { list -> list.map { if (it.id == libraryId) it.copy(username = username) else it } }
    }

    // #368
    /** When non-null, [rename] throws this instead. */
    var renameFailure: Throwable? = null

    /** The (libraryId, name) pairs passed to [rename], as given, in call order. */
    val renames = mutableListOf<Pair<String, String>>()

    override suspend fun rename(libraryId: String, name: String) {
        renameFailure?.let { throw it }
        renames += libraryId to name
        val trimmed = name.trim()
        val stored = if (trimmed.codePointCount(0, trimmed.length) <= 40) {
            trimmed
        } else {
            trimmed.substring(0, trimmed.offsetByCodePoints(0, 40))
        }
        libraries.update { list -> list.map { if (it.id == libraryId) it.copy(name = stored) else it } }
    }

    // #369
    /** When non-null, [setActive] throws this instead. */
    var setActiveFailure: Throwable? = null

    /** The ids passed to [setActive], as given, in call order. */
    val activations = mutableListOf<String>()

    private var activationCount = 0L

    override suspend fun setActive(libraryId: String) {
        setActiveFailure?.let { throw it }
        activations += libraryId
        if (libraries.value.none { it.id == libraryId }) return
        val stamp = ++activationCount
        libraries.update { list ->
            list.map { if (it.id == libraryId) it.copy(isActive = true, lastUsedAt = stamp) else it.copy(isActive = false) }
        }
    }

    // #371
    /** The addresses passed to [findByAddress], in call order. */
    val foundAddresses = mutableListOf<String>()

    override suspend fun findByAddress(address: String): Library? {
        foundAddresses += address
        if (ServerAddress.normalize(address) == null) return null
        return libraries.value.firstOrNull { sameServerAddress(it.address, address) }
    }
}
