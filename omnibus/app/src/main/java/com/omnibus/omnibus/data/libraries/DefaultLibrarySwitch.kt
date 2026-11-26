package com.omnibus.omnibus.data.libraries

import com.omnibus.omnibus.data.audiobook.playback.AudiobookController
import com.omnibus.omnibus.data.libraries.local.PendingOpeningStore
import com.omnibus.omnibus.di.AppRestarter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** How long a switch waits for playback to stop and save its final position. Just above the controller's unload limit. */
internal val STOP_WAIT: Duration = 3.seconds

/** [LibrarySwitch] over the libraries, the playback controller and the pending opening (C11). */
@Singleton
class DefaultLibrarySwitch @Inject constructor(
    private val libraries: LibrariesRepository,
    private val activeLibrary: ActiveLibrary,
    private val pendingOpening: PendingOpeningStore,
    private val audiobook: AudiobookController,
    private val restarter: AppRestarter,
) : LibrarySwitch {

    override suspend fun commit(targetLibraryId: String, kind: SwitchKind) {
        when (kind) {
            SwitchKind.Switch -> {
                // Pin the old library in the ActiveLibrary cache, so stop()'s final save is scoped to it.
                activeLibrary.id()
                audiobook.stop()
                // stop() saves the final position in a launched coroutine, up to the controller's unload limit.
                withTimeoutOrNull(STOP_WAIT) { audiobook.state.first { it.book == null } }
                // The marker goes before setActive: dying in between leaves a stale marker, which MainViewModel clears.
                pendingOpening.save(PendingOpening(targetLibraryId, kind))
                libraries.setActive(targetLibraryId)
                restarter.restart()
            }
        }
    }

    override fun getPendingOpeningFlow(): Flow<PendingOpening?> = pendingOpening.getFlow()

    override suspend fun clearPendingOpening() = pendingOpening.clear()
}
