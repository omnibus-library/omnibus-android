package com.omnibus.omnibus.data.library

/**
 * The Library's category filter. Each value names the formats a books page is limited to, and [All]
 * sends no format filter. The lists are typed out here rather than taken from [Book], whose format
 * sets include formats the category filter must not send (`kepub`, `wav`).
 *
 * @property formats The formats sent as the `formats` query, in the order they are sent.
 */
enum class LibraryCategory(val formats: Set<String>) {
    All(emptySet()),
    Ebooks(linkedSetOf("epub", "pdf", "mobi", "azw3", "cbz", "cbr")),
    Audiobooks(linkedSetOf("m4b", "m4a", "mp3", "aac", "flac", "ogg", "opus")),
    ;

    /** The page query for this category, ordered by [sort] and [dir]. */
    fun toQuery(sort: LibrarySort, dir: SortDirection): BooksPageQuery =
        BooksPageQuery(sort = sort, dir = dir, formats = formats)
}
