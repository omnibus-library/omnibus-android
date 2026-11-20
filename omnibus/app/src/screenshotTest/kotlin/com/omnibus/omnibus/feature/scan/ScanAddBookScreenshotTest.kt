package com.omnibus.omnibus.feature.scan

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.screenshot.Shot
import kotlinx.serialization.json.JsonObject

/** A provider's record with no cover, so the card shows the lettered plate. */
private val previewRecord = ProviderRecord(
    raw = JsonObject(emptyMap()),
    isbn13 = "9780593321485",
    title = "Bad Blood",
    authors = listOf("John Carreyrou"),
    year = null,
    firstPublishYear = 2018L,
    publisher = "Knopf",
    pages = 352L,
    series = "A story of Theranos",
    coverUrl = null,
)

/** A book the library doesn't have, from a provider with no cover: the card, its facts and the two add actions. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanOutcomeNotInLibrary() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Outcome(ScanOutcome.NotInLibrary(previewRecord), "9780593321485", FoundVia.Scan),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = false,
        )
    }
}

/** A provider record added to the wishlist: a single ring pulse, the badge and the wishlisting line, no confetti. */
@PreviewTest
@PreviewLightDark
@Composable
fun ScanSuccessWishlisted() {
    Shot {
        ScanScreenContent(
            uiState = ScanUIState(
                stage = ScanStage.Success(
                    title = previewRecord.title,
                    cover = SuccessCover.Provider(previewRecord),
                    bookUuid = "preview-wishlist",
                    kind = SuccessKind.Wishlisted,
                ),
            ),
            camera = PreviewCamera(CameraAvailability.Ready),
            actions = ScanActions(),
            wide = false,
            reducedMotion = false,
        )
    }
}
