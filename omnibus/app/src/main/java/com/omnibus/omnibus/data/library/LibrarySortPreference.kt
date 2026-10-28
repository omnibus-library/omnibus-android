package com.omnibus.omnibus.data.library

/**
 * The sort and direction the Library restores on launch. Stored per device. The category is never
 * stored, so every launch starts on [LibraryCategory.All].
 *
 * @property sort The sort key the books are ordered by.
 * @property direction The order the sort key runs in.
 */
data class LibrarySortPreference(
    val sort: LibrarySort = LibrarySort.NewestAdded,
    val direction: SortDirection = SortDirection.Desc,
) {

    companion object {

        /**
         * Decodes the two stored wire tokens. Each field falls back to its own default when its token
         * is missing or unknown, so a valid value for one field survives a bad value for the other.
         */
        fun decode(rawSort: String?, rawDirection: String?): LibrarySortPreference = LibrarySortPreference(
            sort = LibrarySort.fromApiName(rawSort) ?: LibrarySort.NewestAdded,
            direction = SortDirection.fromApiName(rawDirection) ?: SortDirection.Desc,
        )
    }
}
