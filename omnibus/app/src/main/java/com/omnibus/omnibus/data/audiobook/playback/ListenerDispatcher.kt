package com.omnibus.omnibus.data.audiobook.playback

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Fans each [PlaybackListener] call out to every listener on the main thread (C4). One listener's exception goes to
 * [onError] and never stops the others or the controller. [unload] is the one call that runs in parallel and waits, for
 * at most [UNLOAD_TIMEOUT_MILLIS].
 */
internal class ListenerDispatcher(
    private val listeners: Set<PlaybackListener>,
    private val onError: (Throwable) -> Unit,
) {

    fun settled(state: PlaybackState, startSeconds: Double) = each { it.onSettled(state, startSeconds) }

    fun tick(state: PlaybackState) = each { it.onTick(state) }

    fun play(state: PlaybackState) = each { it.onPlay(state) }

    fun pause(state: PlaybackState) = each { it.onPause(state) }

    fun appBackground(state: PlaybackState) = each { it.onAppBackground(state) }

    fun ended(state: PlaybackState) = each { it.onEnded(state) }

    /**
     * Runs every `onUnload` in parallel, and waits at most [UNLOAD_TIMEOUT_MILLIS] (2 s). A listener still running after
     * that is cancelled and the caller carries on, so a slow listener can't hold up a stop or a book switch. Never throws
     * except cancellation.
     */
    suspend fun unload(state: PlaybackState, reason: UnloadReason) {
        if (listeners.isEmpty()) return
        withTimeoutOrNull(UNLOAD_TIMEOUT_MILLIS) {
            coroutineScope {
                listeners.forEach { listener ->
                    launch {
                        try {
                            listener.onUnload(state, reason)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (t: Throwable) {
                            onError(t)
                        }
                    }
                }
            }
        }
    }

    private inline fun each(block: (PlaybackListener) -> Unit) = listeners.forEach { listener ->
        try {
            block(listener)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            onError(t)
        }
    }

    companion object {
        /** How long a stop or a book switch waits for `onUnload`, in milliseconds (C4). */
        const val UNLOAD_TIMEOUT_MILLIS = 2_000L
    }
}
