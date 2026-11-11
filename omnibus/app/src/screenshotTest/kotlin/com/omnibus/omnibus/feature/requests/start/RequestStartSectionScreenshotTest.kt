package com.omnibus.omnibus.feature.requests.start

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.screenshot.Shot

@Composable
private fun SlotsShot(kinds: List<RequestKind>, starting: RequestKind? = null) {
    Shot { RequestSlots(kinds, starting, isOnline = true, wording = RequestWording(), onStart = {}, modifier = Modifier.padding(16.dp)) }
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 160)
@Preview(name = "Dark", widthDp = 411, heightDp = 160, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestSlotsOneKind() {
    SlotsShot(listOf(RequestKind.Audiobook))
}

@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 240)
@Preview(name = "Dark", widthDp = 411, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestSlotsBothKinds() {
    SlotsShot(listOf(RequestKind.Ebook, RequestKind.Audiobook))
}

@PreviewTest
@Preview(name = "Dark", widthDp = 411, heightDp = 240, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RequestSlotsStarting() {
    SlotsShot(listOf(RequestKind.Ebook, RequestKind.Audiobook), starting = RequestKind.Ebook)
}
