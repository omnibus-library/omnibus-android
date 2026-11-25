package com.omnibus.omnibus.feature.libraries.manage

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.screenshot.Shot

// Fixed libraries, not random: the screenshots must be stable. The second is renamed, the third has no name and shows
// its host with the Maltese cross.
private val libraries = listOf(
    Library("home", "https://books.home.lan", "Home", "hoon", addedAt = 1, lastUsedAt = 1, isActive = true),
    Library("work", "https://library.acme.io", "Work book club", "hjames", addedAt = 2, lastUsedAt = 2, isActive = false),
    Library("bare", "http://10.0.0.40:3000", "", null, addedAt = 3, lastUsedAt = 3, isActive = false),
)

/** The list on a phone, and from 600dp wide, where its content is capped at 560dp and centred. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Wide", widthDp = 840, heightDp = 600)
@Composable
fun ManageLibraries() {
    Shot { ManageLibrariesScreenContent(libraries = libraries, onBack = {}, onOpenLibrary = {}) }
}
