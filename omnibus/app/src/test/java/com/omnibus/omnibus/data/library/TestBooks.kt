package com.omnibus.omnibus.data.library

/** A [Book] with every optional field empty, for tests that only care about a few fields. */
fun testBook(
    id: Long,
    title: String? = "Book $id",
    creators: List<Creator> = listOf(Creator("Author $id", null, null, null)),
    formats: List<String> = listOf("epub"),
    uniqueIdentifier: String? = "uuid-$id",
    filename: String = "book-$id.epub",
): Book = Book(
    id = id,
    uniqueIdentifier = uniqueIdentifier,
    filename = filename,
    title = title,
    creators = creators,
    coverUrl = null,
    accent = null,
    formats = formats,
    series = null,
    seriesIndex = null,
    hasPhysical = false,
    addedAt = null,
    lastInteractedAt = null,
    bookFiles = emptyList(),
)
