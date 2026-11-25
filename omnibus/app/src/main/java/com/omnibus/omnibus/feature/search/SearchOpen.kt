package com.omnibus.omnibus.feature.search

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination

/** Where a tap on the Search tab leads. The shell resolves it on the Search stack. */
sealed interface SearchOpen {
    /** Opens [destination], such as a book's Book Details. */
    data class Destination(val destination: BookDetailDestination) : SearchOpen

    /** Pushes [route], such as the See all list of a section. */
    data class Route(val route: NavKey) : SearchOpen
}
