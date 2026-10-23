package com.omnibus.omnibus.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Keeps lazily shared flows (`SharingStarted.Lazily` / `WhileSubscribed`) active during a test.
 *
 * Runs on `Dispatchers.Main`, so set it to an `UnconfinedTestDispatcher` first; collection then
 * happens eagerly and `StateFlow.value` is current as soon as an action returns.
 */
class CollectingScope {
    private val scope = CoroutineScope(Job() + Dispatchers.Main)

    fun collect(flow: Flow<*>) {
        scope.launch { flow.collect {} }
    }

    fun cancel() = scope.cancel()
}
