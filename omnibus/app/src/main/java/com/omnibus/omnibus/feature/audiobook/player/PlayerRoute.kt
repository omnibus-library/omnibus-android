package com.omnibus.omnibus.feature.audiobook.player

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The audiobook player's destination. It goes on the root back stack, not a tab stack, so the player covers the shell
 * and its tab bar, and closing it returns to the shell with its tab stacks intact.
 *
 * The arguments match the old `PlayerStubRoute`, so saved payloads still decode.
 *
 * @property bookUuid The book to play.
 * @property bookFileId The file to open, or null for the saved one.
 * @property positionSeconds Where to start, in seconds, or null for the saved place.
 */
@Serializable
data class PlayerRoute(val bookUuid: String, val bookFileId: Long?, val positionSeconds: Double? = null) : NavKey
