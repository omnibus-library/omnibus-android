package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** An [AppBackgroundSignal] that a test fires with [emit]. Collect it before emitting, since nothing is buffered. */
class TestAppBackgroundSignal : AppBackgroundSignal {

    private val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override val backgrounded: Flow<Unit> = events

    /** Signals that the app went to the background. */
    fun emit() {
        events.tryEmit(Unit)
    }
}
