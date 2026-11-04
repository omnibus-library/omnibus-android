package com.omnibus.omnibus.data.audiobook.playback

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** Emits when the app goes to the background (R10's `onAppBackground`). */
interface AppBackgroundSignal {

    /** One emission each time the app stops, which is when it leaves the foreground. */
    val backgrounded: Flow<Unit>
}

/**
 * Reports the process going to the background, from `ProcessLifecycleOwner`. Collect it on the main thread: the observer
 * is added and removed there.
 */
class ProcessLifecycleAppBackgroundSignal internal constructor(private val lifecycle: Lifecycle) : AppBackgroundSignal {

    @Inject
    constructor() : this(ProcessLifecycleOwner.get().lifecycle)

    override val backgrounded: Flow<Unit> = callbackFlow {
        val observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                trySend(Unit)
            }
        }
        lifecycle.addObserver(observer)
        awaitClose { lifecycle.removeObserver(observer) }
    }
}
