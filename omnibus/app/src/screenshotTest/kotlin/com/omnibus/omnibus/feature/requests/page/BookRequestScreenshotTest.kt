package com.omnibus.omnibus.feature.requests.page

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.requests.CoverOffer
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.StuckReason
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.SHOT_NOW_MILLIS
import com.omnibus.omnibus.feature.requests.SHOT_NOW_SECS
import com.omnibus.omnibus.feature.requests.ShotAsker
import com.omnibus.omnibus.feature.requests.shotPage
import com.omnibus.omnibus.feature.requests.shotRequest
import com.omnibus.omnibus.feature.requests.shotVersions
import com.omnibus.omnibus.screenshot.Shot

/** The page at the preview's own size, with every control a no-op. */
@Composable
private fun PageShot(state: BookRequestUIState) {
    Shot {
        BookRequestScreenContent(
            uiState = state,
            wording = RequestWording(),
            onBack = {},
            onOpenBook = {},
            onRetry = {},
            actions = RequestActions(),
            onMessageDismissed = {},
        )
    }
}

// Each state at phone size in light and dark, then two-pane (a tablet or a foldable flat, in landscape).

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLookingPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Looking))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLookingTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Looking))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLookingSlowPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Looking, updatedAt = SHOT_NOW_SECS - 40))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLookingSlowTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Looking, updatedAt = SHOT_NOW_SECS - 40))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLookingStalePhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Looking, updatedAt = SHOT_NOW_SECS - 200))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLookingStaleTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Looking, updatedAt = SHOT_NOW_SECS - 200))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChoosePhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Choose, versions = shotVersions()))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChooseTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Choose, versions = shotVersions()))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChooseAskerPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Choose, versions = shotVersions())), viewer = ShotAsker))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChooseAskerTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Choose, versions = shotVersions())), viewer = ShotAsker))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChosenPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Chosen, chosen = 1, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChosenTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Chosen, chosen = 1, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestOnItsWay4Phone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, chosen = 1, progress = 0.04, expectedDoneAt = SHOT_NOW_SECS + 5_400, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestOnItsWay4TwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, chosen = 1, progress = 0.04, expectedDoneAt = SHOT_NOW_SECS + 5_400, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestOnItsWay68Phone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, chosen = 1, progress = 0.68, expectedDoneAt = SHOT_NOW_SECS + 240, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestOnItsWay68TwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, chosen = 1, progress = 0.68, expectedDoneAt = SHOT_NOW_SECS + 240, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestAddingPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, chosen = 1, progress = 1.0, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestAddingTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, chosen = 1, progress = 1.0, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestReadyPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Ready, chosen = 1, progress = 1.0, updatedAt = SHOT_NOW_SECS - 120, versions = shotVersions().take(1)))))
}

/** An ebook and an audiobook both finished: each stays as a lane in its Ready state. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestTwoReadyPhone() {
    PageShot(
        shotPage(
            listOf(
                shotRequest(RequestState.Ready, id = 2, kind = RequestKind.Audiobook, chosen = 1, progress = 1.0, updatedAt = SHOT_NOW_SECS - 60, versions = shotVersions().take(1)),
                shotRequest(RequestState.Ready, id = 1, chosen = 1, progress = 1.0, updatedAt = SHOT_NOW_SECS - 120, versions = shotVersions().take(1)),
            ),
        ),
    )
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestReadyTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Ready, chosen = 1, progress = 1.0, updatedAt = SHOT_NOW_SECS - 120, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestReadyCoverOfferPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Ready, chosen = 1, progress = 1.0, updatedAt = SHOT_NOW_SECS - 120, versions = shotVersions().take(1), coverOffer = CoverOffer(600, 900, 128, 196)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestReadyCoverOfferTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Ready, chosen = 1, progress = 1.0, updatedAt = SHOT_NOW_SECS - 120, versions = shotVersions().take(1), coverOffer = CoverOffer(600, 900, 128, 196)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestStuckNotFoundPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Stuck, stuckReason = StuckReason.NotFound))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestStuckNotFoundTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Stuck, stuckReason = StuckReason.NotFound))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestStuckStoppedPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Stuck, stuckReason = StuckReason.StoppedMoving, stuckDetail = "qBittorrent made no progress for 12 hours", chosen = 1, progress = 0.4, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestStuckStoppedTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Stuck, stuckReason = StuckReason.StoppedMoving, stuckDetail = "qBittorrent made no progress for 12 hours", chosen = 1, progress = 0.4, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestStuckCouldntAddPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Stuck, stuckReason = StuckReason.CouldntAdd, chosen = 1, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestStuckCouldntAddTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Stuck, stuckReason = StuckReason.CouldntAdd, chosen = 1, versions = shotVersions().take(1)))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestCancelledUndoPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Cancelled, cancelAt = SHOT_NOW_SECS + 8, cancelledBy = 1)), message = RequestMessage("Ebook request cancelled", undoRequestId = 1, textDeadlineMillis = SHOT_NOW_MILLIS + 8_000, undoDeadlineMillis = SHOT_NOW_MILLIS + 8_000, id = 1)))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestCancelledUndoTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Cancelled, cancelAt = SHOT_NOW_SECS + 8, cancelledBy = 1)), message = RequestMessage("Ebook request cancelled", undoRequestId = 1, textDeadlineMillis = SHOT_NOW_MILLIS + 8_000, undoDeadlineMillis = SHOT_NOW_MILLIS + 8_000, id = 1)))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestCancelledAgainPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Cancelled, cancelAt = SHOT_NOW_SECS - 30, cancelledBy = 1))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestCancelledAgainTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Cancelled, cancelAt = SHOT_NOW_SECS - 30, cancelledBy = 1))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestHistoryPhone() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, id = 3, chosen = 1, progress = 0.3, versions = shotVersions().take(1))), history = listOf(shotRequest(RequestState.Cancelled, id = 2, kind = RequestKind.Audiobook, cancelledBy = 2, progress = 0.4, chosen = 1, versions = shotVersions().take(1), updatedAt = SHOT_NOW_SECS - 200_000), shotRequest(RequestState.Ready, id = 1, chosen = 1, versions = shotVersions().take(2).mapIndexed { i, v -> if (i == 1) v.copy(rejected = true) else v }, updatedAt = SHOT_NOW_SECS - 900_000))))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestHistoryTwoPane() {
    PageShot(shotPage(listOf(shotRequest(RequestState.OnItsWay, id = 3, chosen = 1, progress = 0.3, versions = shotVersions().take(1))), history = listOf(shotRequest(RequestState.Cancelled, id = 2, kind = RequestKind.Audiobook, cancelledBy = 2, progress = 0.4, chosen = 1, versions = shotVersions().take(1), updatedAt = SHOT_NOW_SECS - 200_000), shotRequest(RequestState.Ready, id = 1, chosen = 1, versions = shotVersions().take(2).mapIndexed { i, v -> if (i == 1) v.copy(rejected = true) else v }, updatedAt = SHOT_NOW_SECS - 900_000))))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLoadErrorPhone() {
    PageShot(BookRequestUIState(failure = RequestsError.Unreachable))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLoadErrorTwoPane() {
    PageShot(BookRequestUIState(failure = RequestsError.Unreachable))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestNotFoundPhone() {
    PageShot(BookRequestUIState(isMissing = true))
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestNotFoundTwoPane() {
    PageShot(BookRequestUIState(isMissing = true))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLoadingPhone() {
    PageShot(BookRequestUIState())
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestLoadingTwoPane() {
    PageShot(BookRequestUIState())
}

/** A foldable opened flat in portrait: one column, capped at 600 dp so each Choose stays beside its title. */
@PreviewTest
@Preview(name = "Light", widthDp = 841, heightDp = 900)
@Preview(name = "Dark", widthDp = 841, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestChooseUnfoldedPortrait() {
    PageShot(shotPage(listOf(shotRequest(RequestState.Choose, versions = shotVersions()))))
}
