package com.omnibus.omnibus.data.journals

import android.util.Log
import com.omnibus.omnibus.data.journals.remote.JournalEntryDto
import com.omnibus.omnibus.data.journals.remote.JournalsJson
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * Default [JournalsRepository]. Keeps one hot state per book uuid. Fetches run in the application scope, so leaving the
 * page cannot cancel them. A failed fetch never replaces a loaded list. Every failure becomes a state, so no exception
 * leaves this class except cancellation.
 */
@Singleton
class DefaultJournalsRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : JournalsRepository {

    /** One book's state. Everything is guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<JournalsState>(JournalsState.Loading)
        var inFlightFetch: Deferred<JournalsState>? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(uuid: String): Entry = entries.computeIfAbsent(uuid) { Entry() }

    override fun getJournalsFlow(bookUuid: String): Flow<JournalsState> {
        val entry = entry(bookUuid)
        return entry.state.onStart {
            if (entry.state.value !is JournalsState.Loaded) startFetch(bookUuid, entry)
        }
    }

    override suspend fun refresh(bookUuid: String) {
        startFetch(bookUuid, entry(bookUuid)).await()
    }

    /**
     * Starts a GET, or returns the one already in flight. It runs in the application scope, not the caller's. The in-flight
     * slot is cleared in the same lock as the state write, so a caller that sees Failed always starts a new fetch.
     */
    private fun startFetch(uuid: String, entry: Entry): Deferred<JournalsState> {
        val deferred = synchronized(entry.lock) {
            entry.inFlightFetch?.takeIf { !it.isCompleted }?.let { return it }
            applicationScope.async(start = CoroutineStart.LAZY) {
                // A retry after a failure shows progress; a loaded list stays on screen while it refreshes.
                synchronized(entry.lock) {
                    if (entry.state.value is JournalsState.Failed) entry.state.value = JournalsState.Loading
                }
                val outcome = get(uuid)
                synchronized(entry.lock) {
                    if (outcome is JournalsState.Loaded) {
                        entry.state.value = outcome
                    } else if (entry.state.value !is JournalsState.Loaded) {
                        entry.state.value = JournalsState.Failed
                    }
                    entry.inFlightFetch = null
                }
                outcome
            }.also { entry.inFlightFetch = it }
        }
        deferred.start()
        return deferred
    }

    /** One GET of the book's journal. Any failure, including a body that does not decode, reads as Failed. */
    private suspend fun get(uuid: String): JournalsState = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext JournalsState.Failed
        val builder = omnibusHttpClient.request(JOURNALS_PATH) ?: return@withContext JournalsState.Failed
        val url = builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    JournalsState.Failed
                } else {
                    val rows = JournalsJson.decodeFromString<List<JournalEntryDto>>(response.body.string())
                    JournalsState.Loaded(rows.map { it.toDomain() })
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read the journal", ex)
            JournalsState.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a journal read", ex)
            JournalsState.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading the journal", ex)
            JournalsState.Failed
        }
    }

    internal companion object {
        const val TAG = "DefaultJournalsRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val JOURNALS_PATH = "api/journals/book"
    }
}
