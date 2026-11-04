package com.omnibus.omnibus.data.audiobook.position

import android.util.Log
import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.audiobook.playback.PlaybackListener
import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.PlayingBook
import com.omnibus.omnibus.data.audiobook.playback.UnloadReason
import com.omnibus.omnibus.data.progress.PositionPushThrottle
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.TimeSource
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Saves the audio position and offers a late server position (C8, I-SAVE-1 to I-SAVE-3, I-RES-5, I-RES-6).
 *
 * Writes happen only while a load is settled and the loaded book and file are still the settled ones, and only when the
 * position is above 0 (I-RES-5). A tick saves locally and pushes at most every 5 s (I-SAVE-2). Pause, background, end,
 * unload and [forceSave] always push and re-arm the throttle (I-SAVE-3).
 *
 * Main thread only: the callbacks and [forceSave] callers run there, and [PositionPushThrottle] is single-threaded.
 *
 * [controller] is a [Lazy] because the controller injects the listener set, and this class is in it.
 */
@Singleton
class DefaultListeningPositionSync @Inject constructor(
    private val repository: ListeningProgressRepository,
    private val controller: Lazy<AudiobookController>,
    private val timeSource: TimeSource,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ListeningPositionSync, PlaybackListener {

    /** The book and file of the settled load, or null when no load is settled. */
    private var settled: PlayingBook? = null

    /** Changes at each new load and each unload, so an offer from an earlier load is dropped (I-RES-6). */
    private var token = 0

    private var throttle = PositionPushThrottle(intervalMillis = PUSH_INTERVAL_MILLIS)

    private val _late = MutableStateFlow<LatePosition?>(null)

    override val lateServerPosition: StateFlow<LatePosition?> = _late.asStateFlow()

    /** Starts a new load: unsettles the last one, clears any offer, and returns the load's token. */
    internal fun loadBegan(): Int {
        settled = null
        _late.value = null
        return ++token
    }

    /** Whether [token] is the current load's, so its answers may still be offered. */
    internal fun isCurrent(token: Int): Boolean = token == this.token

    /** Offers [position] when [token] is the current load's. */
    internal fun offerLate(token: Int, position: LatePosition) {
        if (isCurrent(token)) _late.value = position
    }

    override fun onSettled(state: PlaybackState, startSeconds: Double) {
        settled = state.book
        throttle = PositionPushThrottle(intervalMillis = PUSH_INTERVAL_MILLIS)
    }

    override fun onTick(state: PlaybackState) {
        val position = positionFor(state) ?: return
        val push = throttle.shouldPush(force = false, nowMillis = timeSource.nowMillis())
        scope.launch { safeSave(position, push) }
    }

    override fun onPause(state: PlaybackState) = forced(state)

    override fun onAppBackground(state: PlaybackState) = forced(state)

    override fun onEnded(state: PlaybackState) = forced(state)

    /** Unsettles first, then awaits the local save, so a timeout cannot leave the old load settled. */
    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        val position = positionFor(state)
        settled = null
        _late.value = null
        token++
        if (position != null) safeSave(position, push = true)
    }

    override suspend fun forceSave() {
        val position = positionFor(controller.get().state.value) ?: return
        throttle.shouldPush(force = true, nowMillis = timeSource.nowMillis())
        safeSave(position, push = true)
    }

    private fun forced(state: PlaybackState) {
        val position = positionFor(state) ?: return
        throttle.shouldPush(force = true, nowMillis = timeSource.nowMillis())
        scope.launch { safeSave(position, push = true) }
    }

    /** The position to write for [state], or null when the guards (I-RES-5) forbid the write. */
    private fun positionFor(state: PlaybackState): AudioPosition? {
        val book = state.book ?: return null
        val settledBook = settled ?: return null
        if (book.bookUuid != settledBook.bookUuid || book.bookFileId != settledBook.bookFileId) return null
        if (!(state.positionSeconds > 0.0)) return null
        return AudioPosition(book.bookUuid, state.positionSeconds, book.bookFileId, timeSource.nowMillis() / 1000)
    }

    /** Saves [position]. A failure is logged and never thrown (I-SAVE-3). */
    private suspend fun safeSave(position: AudioPosition, push: Boolean) {
        try {
            repository.save(position, push)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.w(TAG, "Couldn't save the audio position", t)
        }
    }

    companion object {
        /** The least time between unforced pushes, in milliseconds (I-SAVE-2). */
        const val PUSH_INTERVAL_MILLIS = 5_000L

        private const val TAG = "ListeningPositionSync"
    }
}
