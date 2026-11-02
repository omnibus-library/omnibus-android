package com.omnibus.omnibus.data.readstatus

import android.util.Log
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import com.omnibus.omnibus.data.readstatus.remote.ReadStatusJson
import com.omnibus.omnibus.data.readstatus.remote.ReadStatusRecordDto
import com.omnibus.omnibus.data.readstatus.remote.ReadStatusUpdateRequestDto
import com.omnibus.omnibus.data.readstatus.remote.token
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.IoDispatcher
import com.omnibus.omnibus.di.TimeSource
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Default [ReadStatusRepository]. Keeps one hot state per book uuid. Writes apply optimistically, run in the
 * application scope so leaving the page cannot strand them, and roll back to the last server answer on failure.
 * Every failure becomes a result, so no exception leaves this class except cancellation.
 */
@Singleton
class DefaultReadStatusRepository @Inject constructor(
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val omnibusHttpClient: OmnibusHttpClient,
    private val timeSource: TimeSource,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) : ReadStatusRepository {

    /** One book's state. Everything but [writeMutex] is guarded by [lock]. */
    private class Entry {
        val lock = Any()
        val state = MutableStateFlow<ReadStatusState>(ReadStatusState.Unknown)
        val writeMutex = Mutex()

        /** The last server answer, which is what a failed write rolls back to. */
        var confirmed: ReadStatusState = ReadStatusState.Unknown

        /** The write sequence that [confirmed] belongs to, so an older answer never replaces a newer one. */
        var confirmedSeq = 0L

        /** Bumped by every write that makes a request. */
        var writeSeq = 0L

        /** Writes whose optimistic value is not yet resolved. When this is zero, [state] equals [confirmed]. */
        var pendingWrites = 0

        var inFlightFetch: Deferred<ReadStatusState>? = null
    }

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun entry(uuid: String): Entry = entries.computeIfAbsent(uuid) { Entry() }

    override fun getReadStatusFlow(bookUuid: String): Flow<ReadStatusState> {
        val entry = entry(bookUuid)
        return entry.state.onStart {
            if (entry.state.value !is ReadStatusState.Known) startFetch(bookUuid, entry)
        }
    }

    override suspend fun refresh(bookUuid: String) {
        fetchStatus(bookUuid)
    }

    override suspend fun fetchStatus(bookUuid: String): ReadStatusState =
        startFetch(bookUuid, entry(bookUuid)).await()

    /** Starts a GET, or returns the one already in flight. It runs in the application scope, not the caller's. */
    private fun startFetch(uuid: String, entry: Entry): Deferred<ReadStatusState> {
        val deferred = synchronized(entry.lock) {
            entry.inFlightFetch?.takeIf { !it.isCompleted }?.let { return it }
            applicationScope.async(start = CoroutineStart.LAZY) {
                val seqAtStart = synchronized(entry.lock) { entry.writeSeq }
                val outcome = get(uuid)
                synchronized(entry.lock) {
                    if (outcome is ReadStatusState.Known) {
                        if (seqAtStart >= entry.confirmedSeq) {
                            entry.confirmed = outcome
                            entry.confirmedSeq = seqAtStart
                        }
                    } else if (entry.confirmed !is ReadStatusState.Known) {
                        entry.confirmed = ReadStatusState.Failed
                    }
                    if (entry.pendingWrites == 0) entry.state.value = entry.confirmed
                }
                outcome
            }.also { entry.inFlightFetch = it }
        }
        deferred.invokeOnCompletion {
            synchronized(entry.lock) {
                if (entry.inFlightFetch === deferred) entry.inFlightFetch = null
            }
        }
        deferred.start()
        return deferred
    }

    override suspend fun setStatus(bookUuid: String, status: ReadStatus): ReadStatusWriteResult {
        if (bookUuid.isBlank()) return ReadStatusWriteResult.Failed(ReadStatusError.BookNotFound)
        val entry = entry(bookUuid)
        val seq = synchronized(entry.lock) {
            val current = entry.state.value
            if (current is ReadStatusState.Known && current.record.status == status) {
                return ReadStatusWriteResult.Unchanged
            }
            entry.pendingWrites++
            entry.state.value = ReadStatusState.Known(optimistic(current, status, timeSource.nowMillis()))
            ++entry.writeSeq
        }
        return applicationScope.async {
            val result = entry.writeMutex.withLock { put(bookUuid, status) }
            synchronized(entry.lock) {
                entry.pendingWrites--
                if (result is ReadStatusWriteResult.Saved && seq >= entry.confirmedSeq) {
                    entry.confirmed = ReadStatusState.Known(result.record)
                    entry.confirmedSeq = seq
                }
                // Only the latest write sets the state: the server's answer on success, or the confirmed one on failure.
                if (seq == entry.writeSeq) entry.state.value = entry.confirmed
            }
            result
        }.await()
    }

    /** The record shown before the server answers. A move into Finished takes the time now, unless it was already Finished. */
    private fun optimistic(current: ReadStatusState, status: ReadStatus, now: Long) = ReadStatusRecord(
        status = status,
        updatedAtMillis = now,
        finishedAtMillis = when {
            status != ReadStatus.Finished -> null
            current is ReadStatusState.Known && current.record.status == ReadStatus.Finished -> current.record.finishedAtMillis
            else -> now
        },
    )

    /** One GET of the book's status. A `null` body means no row, which reads as Unread. */
    private suspend fun get(uuid: String): ReadStatusState = withContext(ioDispatcher) {
        if (uuid.isBlank()) return@withContext ReadStatusState.Failed
        val builder = omnibusHttpClient.request(READ_STATUS_PATH) ?: return@withContext ReadStatusState.Failed
        val url = builder.build().url.newBuilder()
            .addPathSegment(uuid)
            .build()
        val request = builder.url(url).get().build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                if (!response.isSuccessful) {
                    ReadStatusState.Failed
                } else {
                    val record = ReadStatusJson.decodeFromString<ReadStatusRecordDto?>(response.body.string())
                    ReadStatusState.Known(
                        record?.toDomain() ?: ReadStatusRecord(ReadStatus.Unread, null, null),
                    )
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to read a status", ex)
            ReadStatusState.Failed
        } catch (ex: IllegalArgumentException) {
            // SerializationException is a subtype, so a body that doesn't decode lands here too.
            Log.w(TAG, "Unexpected response to a status read", ex)
            ReadStatusState.Failed
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure reading a status", ex)
            ReadStatusState.Failed
        }
    }

    /** One PUT of [status]. The result is the server's record, or the error the write failed with. */
    private suspend fun put(uuid: String, status: ReadStatus): ReadStatusWriteResult = withContext(ioDispatcher) {
        val builder = omnibusHttpClient.request(READ_STATUS_PATH)
            ?: return@withContext ReadStatusWriteResult.Failed(ReadStatusError.NoServer)
        val body = ReadStatusUpdateRequestDto(bookUuid = uuid, status = status.token()).toJsonRequest()
        val request = builder.put(body).build()

        try {
            omnibusHttpClient.execute(request).use { response ->
                when {
                    response.isSuccessful -> {
                        val record = ReadStatusJson.decodeFromString<ReadStatusRecordDto?>(response.body.string())
                        record?.toDomain()
                            ?.let { ReadStatusWriteResult.Saved(it) }
                            ?: ReadStatusWriteResult.Failed(ReadStatusError.UnexpectedResponse)
                    }
                    response.code == HTTP_UNAUTHORIZED -> ReadStatusWriteResult.Failed(ReadStatusError.SessionExpired)
                    response.code == HTTP_NOT_FOUND -> ReadStatusWriteResult.Failed(ReadStatusError.BookNotFound)
                    else -> ReadStatusWriteResult.Failed(ReadStatusError.Http(response.code))
                }
            }
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: IOException) {
            Log.w(TAG, "Unable to reach the server to set a status", ex)
            ReadStatusWriteResult.Failed(ReadStatusError.Unreachable)
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "Unexpected response to a status write", ex)
            ReadStatusWriteResult.Failed(ReadStatusError.UnexpectedResponse)
        } catch (ex: Exception) {
            Log.e(TAG, "Unexpected failure setting a status", ex)
            ReadStatusWriteResult.Failed(ReadStatusError.UnexpectedResponse)
        }
    }

    internal companion object {
        const val TAG = "DefaultReadStatusRepository"

        /** Relative, so it resolves beneath the server's base URL. */
        const val READ_STATUS_PATH = "api/read-status"

        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
    }
}
