package com.omnibus.omnibus.data.audiobook.position

import android.util.Log
import com.omnibus.omnibus.data.audiobook.playback.DefaultAudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.ClientIdSource
import com.omnibus.omnibus.di.TimeSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Adds up each book's listening time and queues one session report per checkpoint (I-SESS-1, I-SESS-3, P-SYNC-4).
 *
 * Each tick adds 0.5 s of wall-clock time whatever the rate, because the controller times its ticks by the wall clock,
 * so the rate is already in the count (I-SESS-2). A checkpoint with at least 5 s queues a report and zeroes the total.
 * A shorter remainder carries to the next checkpoint of the same book, and an unload drops it.
 *
 * Main thread only, like the other listeners.
 */
@Singleton
class ListeningSessionTracker @Inject constructor(
    private val repository: ListeningProgressRepository,
    private val timeSource: TimeSource,
    private val clientIds: ClientIdSource,
    private val zone: DeviceTimeZone,
    @param:ApplicationScope private val scope: CoroutineScope,
) : PlaybackListener {

    /** The book the total belongs to, or null after an unload. */
    private var bookUuid: String? = null

    /** Seconds listened to [bookUuid] since its last report or restart. */
    private var listenedSeconds = 0.0

    override fun onTick(state: PlaybackState) {
        val uuid = state.book?.bookUuid ?: return
        if (uuid != bookUuid) {
            bookUuid = uuid
            listenedSeconds = 0.0
        }
        listenedSeconds += TICK_SECONDS
    }

    override fun onPause(state: PlaybackState) = queueCheckpoint()

    override fun onAppBackground(state: PlaybackState) = queueCheckpoint()

    override fun onEnded(state: PlaybackState) = queueCheckpoint()

    /** Awaits the queueing, so the report is in local storage when the unload returns. */
    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        val report = checkpoint()
        bookUuid = null
        listenedSeconds = 0.0
        report?.let { safeQueue(it) }
    }

    private fun queueCheckpoint() {
        val report = checkpoint() ?: return
        scope.launch { safeQueue(report) }
    }

    /** The report for the total so far, or null when it is under [MIN_REPORT_SECONDS]. A report zeroes the total. */
    private fun checkpoint(): ListeningSessionReport? {
        val uuid = bookUuid ?: return null
        if (listenedSeconds < MIN_REPORT_SECONDS) return null
        val endedAtMillis = timeSource.nowMillis()
        val endedAt = endedAtMillis / 1000
        val units = floor(listenedSeconds).toLong()
        val tz = zone.current()
        listenedSeconds = 0.0
        return ListeningSessionReport(
            bookUuid = uuid,
            startedAt = endedAt - units,
            endedAt = endedAt,
            progressUnits = units,
            clientId = clientIds.mint(),
            utcOffsetMinutes = tz.getOffset(endedAtMillis) / 60_000,
            timeZone = tz.id,
        )
    }

    /** Queues [report]. A failure is logged and never thrown. */
    private suspend fun safeQueue(report: ListeningSessionReport) {
        try {
            repository.queueSession(report)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't queue a listening session", t)
        }
    }

    companion object {
        /** A checkpoint below this total is not reported, and carries over to the next one (I-SESS-3). */
        const val MIN_REPORT_SECONDS = 5.0

        /** One tick in seconds. Wall-clock time, so it is not divided by the rate (I-SESS-2). */
        private const val TICK_SECONDS = DefaultAudiobookController.TICK_MILLIS / 1000.0

        private const val TAG = "ListeningSessionTracker"
    }
}
