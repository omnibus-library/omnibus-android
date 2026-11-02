package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.compose.runtime.Immutable

/** The list the user tapped a book's cover from: the All books grid or the Continue carousel. */
internal enum class BookCoverOrigin { Grid, Continue }

/** One cover that can take part in the transition: a book, shown from one origin. */
@Immutable
internal data class BookCoverKey(val uuid: String, val origin: BookCoverOrigin)
