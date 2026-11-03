package com.omnibus.omnibus.feature.bookdetail.more.merge

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator

/** A library book for merge tests, with a uuid, an author and the given formats. */
internal fun mergeTestBook(
    id: Long,
    uuid: String? = "uuid-$id",
    title: String = "Book $id",
    author: String? = "Author $id",
    formats: List<String> = listOf("epub"),
    seriesId: Long? = null,
    published: String? = null,
): Book = Book(
    id = id,
    uniqueIdentifier = uuid,
    filename = "book-$id.epub",
    title = title,
    creators = author?.let { listOf(Creator(it, "author", null, null)) } ?: emptyList(),
    coverUrl = null,
    accent = null,
    formats = formats,
    series = null,
    seriesIndex = null,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
    publishedDate = published,
    seriesId = seriesId,
)
