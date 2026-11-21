package com.omnibus.omnibus.data.scan

import com.omnibus.omnibus.data.wishlist.WishlistSource

/** The scan routes: looking an ISBN up, and checking a copy in. Every failure is a result, not an exception. */
interface ScanRepository {

    /** Looks [isbn] (ISBN-10 or -13, as typed or scanned) up against the library and the providers. */
    suspend fun resolve(isbn: String): ScanResult<ScanOutcome>

    /**
     * Checks a physical copy of [bookUuid] in, filed under [isbn] (the ISBN in hand). Runs to completion even if the
     * caller is cancelled. Success holds the uuid the server wrote to, which a merge can make differ from [bookUuid].
     */
    suspend fun checkIn(bookUuid: String, isbn: String?, note: String?): ScanResult<String>

    /** Creates a book with no files from [record] and checks in its first copy. Success holds the new book's uuid. */
    suspend fun addPhysicalOnly(record: ProviderRecord): ScanResult<String>

    /** Puts a new book from [record] on the reader's wishlist, recorded as coming from [source]. */
    suspend fun addToWishlist(record: ProviderRecord, source: WishlistSource): ScanResult<String>

    /** Provider records whose title matches [query] (trimmed), at most 20. An empty success means no match. */
    suspend fun searchByTitle(query: String): ScanResult<List<ProviderRecord>>

    /** Matches a provider record the reader picked from [searchByTitle] against the library, without a second provider lookup. */
    suspend fun resolveRecord(record: ProviderRecord): ScanResult<ScanOutcome>
}
