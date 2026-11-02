package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * The Book Detail page's push destination for one book.
 *
 * @property uuid The book's stable identity, as [com.omnibus.omnibus.data.library.Book.uuid].
 */
@Serializable
data class BookDetailRoute(val uuid: String) : NavKey
