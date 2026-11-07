package com.omnibus.omnibus.data.audiobook.position

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [ListeningPositionSync] whose late offer tests set directly. [forceSave] counts its calls and writes nothing. Shared
 * with the Player Screen tests (#166).
 */
class TestListeningPositionSync : ListeningPositionSync {

    /** The offer a test sets. Starts null, as the real sync does. */
    override val lateServerPosition: MutableStateFlow<LatePosition?> = MutableStateFlow(null)

    /** How many times [forceSave] was called. */
    var forceSaveCount = 0
        private set

    override suspend fun forceSave() {
        forceSaveCount++
    }
}
