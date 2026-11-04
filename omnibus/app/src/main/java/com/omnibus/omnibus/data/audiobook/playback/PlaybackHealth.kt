package com.omnibus.omnibus.data.audiobook.playback

/** What the player reported, reduced to the three cases health cares about. */
internal enum class PlaybackSignal { Playing, Starved, Failed }

/** What the controller should do about a [PlaybackSignal]. */
internal sealed interface HealthAction {
    /** Playback is fine. */
    data object Healthy : HealthAction

    /** The player is waiting for data. Never a reason to rebuild (I-HLTH-1). */
    data object Buffering : HealthAction

    /** Rebuild the item at the current position, after waiting [attempt] seconds (I-HLTH-2). */
    data class Rebuild(val attempt: Int) : HealthAction

    /** Give up and leave the item paused with an error (I-HLTH-3). */
    data object Surrender : HealthAction
}

/**
 * Counts consecutive failures for one loaded item, a port of iOS `PlaybackHealth`. Real playback resets the count, and
 * buffering does not touch it, so a slow network never uses up the budget.
 */
internal class PlaybackHealth {

    /** Failures since the last real playback or [reset]. */
    var consecutiveFailures = 0
        private set

    /** The action for [signal], updating the failure count. */
    fun observed(signal: PlaybackSignal): HealthAction = when (signal) {
        PlaybackSignal.Playing -> {
            consecutiveFailures = 0
            HealthAction.Healthy
        }
        PlaybackSignal.Starved -> HealthAction.Buffering
        PlaybackSignal.Failed -> {
            consecutiveFailures++
            if (consecutiveFailures <= REBUILD_BUDGET) {
                HealthAction.Rebuild(consecutiveFailures)
            } else {
                HealthAction.Surrender
            }
        }
    }

    /** Refills the budget, for a play that asks to retry after [HealthAction.Surrender]. */
    fun reset() {
        consecutiveFailures = 0
    }

    companion object {
        /** Rebuilds allowed before giving up: three, so the fourth failure surrenders (I-HLTH-3). */
        const val REBUILD_BUDGET = 3
    }
}
