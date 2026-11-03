package com.omnibus.omnibus.feature.bookdetail.more.merge

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.testDetailBook

/** The book on the page in merge UI tests. It has a series, so a same-series candidate can be drawn. */
internal fun mergeTarget(): Book = testDetailBook(
    id = 1,
    title = "The Sign of the Four",
    uniqueIdentifier = "book-1",
).copy(seriesId = 5L)

/** A candidate for merge UI tests. A [year] is given as the server's published string. */
internal fun mergeBook(
    id: Long,
    title: String,
    author: String? = "Ann Author",
    year: String? = null,
    formats: List<String> = listOf("epub"),
    seriesId: Long? = null,
    uuid: String = "uuid-$id",
): Book = testDetailBook(
    id = id,
    title = title,
    creators = author?.let { listOf(Creator(it, "author", null, null)) } ?: emptyList(),
    formats = formats,
    uniqueIdentifier = uuid,
    publishedDate = year,
).copy(seriesId = seriesId)

/** The texts of the node, one per `Text` child in the merged tree. */
internal fun SemanticsNodeInteraction.texts(): List<String> =
    runCatching { fetchSemanticsNode().config[SemanticsProperties.Text].map { it.text } }.getOrDefault(emptyList())

/** The vertical position of the node in its root, which is unclipped, so scrolled-out nodes still order correctly. */
internal fun SemanticsNodeInteraction.top(): Float = fetchSemanticsNode().positionInRoot.y

/** The result-count note for [count], from the app's plurals. */
internal fun noteCount(count: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.resources
        .getQuantityString(R.plurals.book_detail_merge_note_count, count, count)
