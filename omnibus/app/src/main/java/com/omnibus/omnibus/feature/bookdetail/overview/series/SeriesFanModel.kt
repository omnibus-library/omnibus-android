package com.omnibus.omnibus.feature.bookdetail.overview.series

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.data.series.seriesNumber

/**
 * What one card says about its book.
 *
 * @property index The book's series number, trimmed, or null when blank.
 * @property isThis Whether the card is the book being viewed.
 * @property isUpNext Whether the card is the next book after this one.
 */
internal data class SeriesCardMeta(val index: String?, val isThis: Boolean, val isUpNext: Boolean)

/**
 * The parts of a card's wording, in reading order: "Book N", then "This book", then "Up next".
 * The caller joins them with its separator.
 */
internal fun SeriesCardMeta.parts(book: (String) -> String, thisBook: String, upNext: String): List<String> = buildList {
    index?.trim()?.takeIf { it.isNotEmpty() }?.let { add(book(it)) }
    if (isThis) add(thisBook)
    if (isUpNext) add(upNext)
}

/** One card of the fan: the member and what it says. */
internal data class SeriesFanCard(val member: SeriesMember, val meta: SeriesCardMeta)

/**
 * The fan for one book.
 *
 * @property current The index of this book in [cards], or null when it is not in the series.
 */
internal data class SeriesFanModel(
    val seriesId: Long,
    val seriesName: String,
    val cards: List<SeriesFanCard>,
    val current: Int?,
)

/**
 * The index of the book "Up next" after [current], or null when there is none.
 *
 * If this book's index is numeric, it is the first later member with a strictly greater numeric index,
 * so a duplicate number is skipped and unnumbered members never count. Otherwise it is the member
 * right after this book in list order.
 */
internal fun upNextIndex(members: List<SeriesMember>, current: Int?): Int? {
    if (current == null || current !in members.indices) return null
    val number = seriesNumber(members[current].seriesIndex)
        ?: return (current + 1).takeIf { it < members.size }
    for (i in current + 1 until members.size) {
        val other = seriesNumber(members[i].seriesIndex) ?: continue
        if (other > number) return i
    }
    return null
}

/**
 * The fan for [book], or null when the section must not show: the book has no series id, its series
 * name is blank, the members are not known yet, or the series has fewer than two members.
 */
internal fun seriesFanModel(book: Book, members: List<SeriesMember>?): SeriesFanModel? {
    val seriesId = book.seriesId ?: return null
    val name = book.series?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (members == null || members.size < 2) return null
    val current = members.indexOfFirst { it.uuid == book.uuid }.takeIf { it >= 0 }
    val upNext = upNextIndex(members, current)
    val cards = members.mapIndexed { i, member ->
        SeriesFanCard(
            member,
            SeriesCardMeta(member.seriesIndex?.trim()?.takeIf { it.isNotEmpty() }, isThis = i == current, isUpNext = i == upNext),
        )
    }
    return SeriesFanModel(seriesId, name, cards, current)
}
