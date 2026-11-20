package com.omnibus.omnibus.data.scan

/** The scan routes: looking an ISBN up, and checking a copy in. Every failure is a result, not an exception. */
interface ScanRepository {

    /** Looks [isbn] (ISBN-10 or -13, as typed or scanned) up against the library and the providers. */
    suspend fun resolve(isbn: String): ScanResult<ScanOutcome>

    /**
     * Checks a physical copy of [bookUuid] in, filed under [isbn] (the ISBN in hand). Runs to completion even if the
     * caller is cancelled. Success holds the uuid the server wrote to, which a merge can make differ from [bookUuid].
     */
    suspend fun checkIn(bookUuid: String, isbn: String?, note: String?): ScanResult<String>
}
