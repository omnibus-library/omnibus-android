package com.omnibus.omnibus.feature.you

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.screenshot.Shot

@Composable
private fun YouShot(state: YouUIState) {
    Shot { YouScreenContent(state, RequestWording(), onOpenRequests = {}) }
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun YouRequestsWithPill() {
    YouShot(YouUIState(showsRequests = true, needsYou = 2))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun YouRequestsWithoutPill() {
    YouShot(YouUIState(showsRequests = true, needsYou = 0))
}
