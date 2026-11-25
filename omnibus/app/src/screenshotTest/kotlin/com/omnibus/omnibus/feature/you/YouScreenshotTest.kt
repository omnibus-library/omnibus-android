package com.omnibus.omnibus.feature.you

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.screenshot.Shot

// A fixed library, not random: the screenshots must be stable.
private val home = Library(
    id = "home",
    address = "https://books.home.lan:8443",
    name = "Home",
    username = "hoon",
    addedAt = 0,
    lastUsedAt = 0,
    isActive = true,
)

@Composable
private fun YouShot(state: YouUIState) {
    Shot { YouScreenContent(state, RequestWording(), onOpenRequests = {}) }
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun YouRequestsWithPill() {
    YouShot(YouUIState(showsRequests = true, needsYou = 2, library = home))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun YouRequestsWithoutPill() {
    YouShot(YouUIState(showsRequests = true, needsYou = 0, library = home))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun YouCardOnly() {
    YouShot(YouUIState(showsRequests = false, library = home.copy(username = null)))
}

/** A wide window: the card and the row lay out at their natural width. */
@PreviewTest
@Preview(name = "Light", widthDp = 840, heightDp = 600)
@Preview(name = "Dark", widthDp = 840, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun YouWide() {
    YouShot(YouUIState(showsRequests = true, needsYou = 2, library = home))
}
