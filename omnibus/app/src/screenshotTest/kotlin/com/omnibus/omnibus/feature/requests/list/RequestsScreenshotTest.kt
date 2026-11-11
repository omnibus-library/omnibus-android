package com.omnibus.omnibus.feature.requests.list

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.SHOT_NOW_SECS
import com.omnibus.omnibus.feature.requests.ShotChooser
import com.omnibus.omnibus.feature.requests.shotListing
import com.omnibus.omnibus.feature.requests.shotRequest
import com.omnibus.omnibus.screenshot.Shot

@Composable
private fun ListShot(state: RequestsUIState) {
    Shot {
        RequestsScreenContent(
            uiState = state,
            wording = RequestWording(),
            onBack = {},
            onRetry = {},
            onRefresh = {},
            onToggleFold = {},
            onOpenRequest = {},
            onAdd = {},
            reducedMotion = true,
        )
    }
}

private fun groups() = RequestsUIState(
    viewer = ShotChooser,
    listings = listOf(
        shotListing("a", "Piranesi", shotRequest(RequestState.Choose, id = 1)),
        shotListing("b", "The Left Hand of Darkness", shotRequest(RequestState.OnItsWay, id = 2, progress = 0.62)),
        shotListing("c", "Dune", shotRequest(RequestState.Looking, id = 3, kind = RequestKind.Audiobook)),
        shotListing("d", "Hyperion", shotRequest(RequestState.Ready, id = 4)),
        shotListing("e", "Neuromancer", shotRequest(RequestState.Cancelled, id = 5, updatedAt = SHOT_NOW_SECS - 50_000)),
    ),
)

/** Needs you, on its way, looking and ready open, with Cancelled folded away. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestsGroupedPhone() {
    ListShot(groups())
}

@PreviewTest
@Preview(name = "TwoPane", widthDp = 1280, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestsGroupedWide() {
    ListShot(groups())
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestsEmpty() {
    ListShot(RequestsUIState(viewer = ShotChooser, listings = emptyList()))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestsError() {
    ListShot(RequestsUIState(viewer = ShotChooser, failure = RequestsError.Unreachable))
}
