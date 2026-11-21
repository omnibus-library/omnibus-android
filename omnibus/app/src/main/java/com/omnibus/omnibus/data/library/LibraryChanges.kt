package com.omnibus.omnibus.data.library

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Tells the Library that books were added outside it, so it reloads without a manual refresh. */
@Singleton
class LibraryChanges @Inject constructor() {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Emits once per [notifyBooksChanged]. A change made while nothing collects is not replayed. */
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /** Signals that the library gained books. */
    fun notifyBooksChanged() {
        _changes.tryEmit(Unit)
    }
}
