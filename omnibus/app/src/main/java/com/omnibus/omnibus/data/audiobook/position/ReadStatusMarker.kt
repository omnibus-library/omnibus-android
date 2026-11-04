package com.omnibus.omnibus.data.audiobook.position

import android.util.Log
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.data.readstatus.ReadStatusRepository
import com.omnibus.omnibus.data.readstatus.ReadStatusState
import com.omnibus.omnibus.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Marks a book Reading on its first play and Finished when it ends (I-SESS-4, P-SYNC-5). Ports iOS `ReadStatusAuto`.
 *
 * The spec does not settle the rules, so these follow iOS. "First play" is the first play of each load. The marker only
 * moves Unread to Reading and never moves a book back from Finished. An unknown or failed fetch writes nothing. A failed
 * write is not retried, since there is no read-status queue.
 *
 * Main thread only, like the other listeners.
 */
@Singleton
class ReadStatusMarker @Inject constructor(
    private val readStatus: ReadStatusRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : PlaybackListener {

    /** The book whose next play is the first of its load, or null once that play has happened. */
    private var markOnPlay: String? = null

    override fun onSettled(state: PlaybackState, startSeconds: Double) {
        markOnPlay = state.book?.bookUuid
    }

    override fun onPlay(state: PlaybackState) {
        val uuid = state.book?.bookUuid ?: return
        if (uuid != markOnPlay) return
        markOnPlay = null
        scope.launch {
            attempt("mark $uuid as reading") {
                val status = readStatus.fetchStatus(uuid)
                if (status is ReadStatusState.Known && status.record.status == ReadStatus.Unread) {
                    readStatus.setStatus(uuid, ReadStatus.Reading)
                }
            }
        }
    }

    override fun onEnded(state: PlaybackState) {
        val uuid = state.book?.bookUuid ?: return
        scope.launch {
            attempt("mark $uuid as finished") {
                val status = readStatus.fetchStatus(uuid)
                if (status is ReadStatusState.Known && status.record.status != ReadStatus.Finished) {
                    readStatus.setStatus(uuid, ReadStatus.Finished)
                }
            }
        }
    }

    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        markOnPlay = null
    }

    /** Runs [block]. A failure is logged and never thrown, and the write is not retried. */
    private suspend fun attempt(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't $what", t)
        }
    }

    private companion object {
        const val TAG = "ReadStatusMarker"
    }
}
