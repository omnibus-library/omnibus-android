package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.data.scan.IsbnRejection
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.scan.cleanIsbn
import com.omnibus.omnibus.data.scan.isbnRejection

/** How the ISBN in hand was found. Only a camera read gets a haptic. */
enum class FoundVia { Scan, Manual }

/** The action whose write is in flight. */
enum class WritingAction { CheckIn }

/** A lookup that failed, kept so "Try again" can repeat it. */
data class LookupFailure(val isbn: String, val via: FoundVia)

/** A barcode that isn't a book's ISBN. [announce] is true when TalkBack should say it. */
data class RejectedNotice(val value: String, val announce: Boolean)

/** The cover shown on the success stage. */
sealed interface SuccessCover {
    /** The library's own cover of [book]. */
    data class Library(val book: ScanBook) : SuccessCover
}

/** Which success message the reader sees: the copy went to the shelf, or came off the wishlist. */
enum class SuccessKind { CheckedIn, OffWishlist }

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

    /** What [isbn] matched. [writing] is set while a check-in is saved. */
    data class Outcome(
        val outcome: ScanOutcome,
        val isbn: String,
        val via: FoundVia,
        val note: String = "",
        val writing: WritingAction? = null,
        val writeFailed: Boolean = false,
    ) : ScanStage

    /** A copy was checked in. */
    data class Success(val title: String, val cover: SuccessCover, val bookUuid: String, val kind: SuccessKind) : ScanStage
}

/** Everything the screen draws. */
data class ScanUIState(val stage: ScanStage = ScanStage.Viewfinder(), val torchOn: Boolean = false)

/** One-shot effects for the screen. */
sealed interface ScanEffect {
    /** A success haptic. */
    data object Confirm : ScanEffect
}
