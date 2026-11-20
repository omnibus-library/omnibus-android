package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.data.scan.IsbnRejection
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.scan.cleanIsbn
import com.omnibus.omnibus.data.scan.isbnRejection
import com.omnibus.omnibus.data.search.LibraryHit

/**
 * How the ISBN in hand was found. Only a camera read gets a haptic. [Search] is a title-search result the reader picked,
 * whose ISBN is the provider record's.
 */
enum class FoundVia { Scan, Manual, Search }

/** The action whose write is in flight. */
enum class WritingAction { CheckIn, Physical, Wishlist }

/**
 * A lookup that failed, kept so "Try again" can repeat it. [record] is set for a picked title-search result, which is
 * matched without a second provider lookup.
 */
data class LookupFailure(val isbn: String, val via: FoundVia, val record: ProviderRecord? = null)

/** A barcode that isn't a book's ISBN. [announce] is true when TalkBack should say it. */
data class RejectedNotice(val value: String, val announce: Boolean)

/** The cover shown on the success stage. */
sealed interface SuccessCover {
    /** The library's own cover of [book]. */
    data class Library(val book: ScanBook) : SuccessCover

    /** The provider's cover of [record], or its lettered plate. */
    data class Provider(val record: ProviderRecord) : SuccessCover
}

/** Which success message the reader sees: the copy went to the shelf, or came off the wishlist. */
enum class SuccessKind { CheckedIn, OffWishlist, Wishlisted }

/** The screen's stages. Only one is shown at a time. */
sealed interface ScanStage {
    /** The camera, with an optional failed-lookup banner and a not-an-ISBN notice. */
    data class Viewfinder(val lookupFailed: LookupFailure? = null, val rejected: RejectedNotice? = null) : ScanStage

    /** Typing an ISBN by hand. [submitted] is true once Find has been tried. */
    data class Manual(val text: String = "", val submitted: Boolean = false) : ScanStage {
        val cleaned: String get() = cleanIsbn(text)
        val rejection: IsbnRejection? get() = isbnRejection(cleaned)
        val canFind: Boolean get() = rejection == null

        /** The rejection to show: none while fewer than 10 characters are typed, unless Find was tried. */
        val shownRejection: IsbnRejection? get() = if (submitted || cleaned.length >= 10) rejection else null
    }

    /** A lookup of [isbn] is in flight. */
    data class Resolving(val isbn: String) : ScanStage

    /**
     * What [isbn] matched. [writing] is set while a write is saved. [wishlistedCandidates] are the uuids of a close
     * match's candidates that are on the wishlist. [declined] is set once the reader said "No, different book". [titleDraft]
     * is the inline title an unresolved ISBN carries into title search.
     */
    data class Outcome(
        val outcome: ScanOutcome,
        val isbn: String,
        val via: FoundVia,
        val note: String = "",
        val writing: WritingAction? = null,
        val writeFailed: Boolean = false,
        val wishlistedCandidates: Set<String> = emptySet(),
        val declined: Boolean = false,
        val titleDraft: String = "",
    ) : ScanStage

    /** A copy was checked in, or a book was added. */
    data class Success(val title: String, val cover: SuccessCover, val bookUuid: String, val kind: SuccessKind) : ScanStage

    /** Searching the providers by title. [results] is null until a search has answered. */
    data class TitleSearch(
        val query: String = "",
        val results: List<ProviderRecord>? = null,
        val loading: Boolean = false,
        val failed: Boolean = false,
    ) : ScanStage

    /** Picking the library book a scan is. [hits] are at most five matches for [query]; [returnTo] is the outcome Back restores. */
    data class LibraryPicker(
        val query: String = "",
        val hits: List<LibraryHit> = emptyList(),
        val total: Int = 0,
        val loading: Boolean = false,
        val failed: Boolean = false,
        val returnTo: Outcome,
    ) : ScanStage
}

/** Everything the screen draws. */
data class ScanUIState(val stage: ScanStage = ScanStage.Viewfinder(), val torchOn: Boolean = false)

/** One-shot effects for the screen. */
sealed interface ScanEffect {
    /** A success haptic. */
    data object Confirm : ScanEffect
}
