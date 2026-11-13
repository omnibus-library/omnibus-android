package com.omnibus.omnibus.feature.shelves.detail

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * One shelf's detail screen, pushed on the stack of the screen that opened it.
 *
 * @property shelfId The shelf's id, as [com.omnibus.omnibus.data.library.Shelf.id].
 */
@Serializable
data class ShelfDetailRoute(val shelfId: Long) : NavKey
