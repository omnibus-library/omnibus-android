package com.omnibus.omnibus.feature.requests

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The Requests list, pushed on the You tab's stack. */
@Serializable
data object RequestsRoute : NavKey

/**
 * One book's request page, pushed on the stack of the screen that asked for it.
 *
 * @property uuid The book's stable identity, as [com.omnibus.omnibus.data.library.Book.uuid].
 */
@Serializable
data class BookRequestRoute(val uuid: String) : NavKey
