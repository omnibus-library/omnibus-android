package com.omnibus.omnibus.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.screenshot.Shot
import kotlinx.serialization.json.JsonObject

private val recoveryCandidates = listOf(
    ScanBook(
        uuid = "preview-dune",
        title = "Dune",
        authors = listOf("Frank Herbert"),
        coverUrl = null,
        hasPhysical = false,
        hasFiles = true,
        isbn = "9780441013593",
    ),
    ScanBook(
        uuid = "preview-dune-audio",
        title = "Dune (audiobook)",
        authors = listOf("Frank Herbert"),
        coverUrl = null,
        hasPhysical = false,
        hasFiles = true,
        isbn = null,
    ),
)

private val recoveryScanned = ProviderRecord(
    raw = JsonObject(emptyMap()),
    isbn13 = "9780441172719",
    title = "Dune",
    authors = listOf("Frank Herbert"),
    year = null,
    firstPublishYear = 1965L,
    publisher = "Ace",
    pages = 412L,
    series = null,
    coverUrl = null,
)

private val recoveryHits = List(5) { index ->
    LibraryHit(
        uuid = "preview-hit-$index",
        title = "Library book ${index + 1}",
        authorDisplay = "Author ${index + 1}",
        year = null,
        formats = listOf("EPUB"),
        coverUrl = null,
        accent = null,
    )
}

/** A scan that matches several library books by title and author; the first is on the wishlist. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanCloseMatchSeveral() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Outcome(
                    outcome = ScanOutcome.CloseMatch(recoveryCandidates, recoveryScanned),
                    isbn = "9780441172719",
                    via = FoundVia.Scan,
                    wishlistedCandidates = setOf("preview-dune"),
                ),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}

/** An ISBN the providers don't know, with an inline title to search for. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanUnresolved() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Outcome(ScanOutcome.Unresolved, "9780441172719", FoundVia.Scan),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}

/** The library picker with a query that has more matches than it lists, so the note shows. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanLibraryPicker() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.LibraryPicker(
                    query = "lo",
                    hits = recoveryHits,
                    total = 23,
                    returnTo = ScanStage.Outcome(ScanOutcome.Unresolved, "9780441172719", FoundVia.Scan),
                ),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = true,
        )
    }
}
