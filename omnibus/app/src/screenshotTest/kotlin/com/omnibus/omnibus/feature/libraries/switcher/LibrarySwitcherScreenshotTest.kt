package com.omnibus.omnibus.feature.libraries.switcher

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing

// Fixed libraries, not random: the screenshots must be stable.
private val libraries = listOf(
    Library("home", "https://books.home.lan", "Home", "hoon", addedAt = 1, lastUsedAt = 1, isActive = true),
    Library("work", "https://library.acme.io", "Work book club", "hjames", addedAt = 2, lastUsedAt = 2, isActive = false),
    Library("mum", "http://10.0.0.12:3000", "Mum's library", "hoon", addedAt = 3, lastUsedAt = 3, isActive = false),
    Library("bare", "http://10.0.0.40:3000", "", null, addedAt = 4, lastUsedAt = 4, isActive = false),
)

/** The sheet's body on a phone: the open library checked in its place, and a library with no name or username. */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 420)
@Preview(name = "Dark", widthDp = 360, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibrarySwitcherPhone() {
    Shot {
        LibrarySwitcherContent(
            libraries,
            onLibraryClick = {},
            modifier = Modifier.background(OmnibusTheme.palette.bg1).padding(top = Spacing.lg),
        )
    }
}

/** The largest font scale on a narrow window: names stay one line and account lines wrap to two at most. */
@PreviewTest
@Preview(name = "Light", widthDp = 320, heightDp = 480, fontScale = 2f)
@Preview(name = "Dark", widthDp = 320, heightDp = 480, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibrarySwitcherLargeFontNarrow() {
    Shot {
        LibrarySwitcherContent(
            libraries.take(2),
            onLibraryClick = {},
            modifier = Modifier.background(OmnibusTheme.palette.bg1).padding(top = Spacing.lg),
        )
    }
}

/** The centred dialog's card on a wide window, capped at 560dp. */
@PreviewTest
@Preview(name = "Light", widthDp = 840, heightDp = 600)
@Preview(name = "Dark", widthDp = 840, heightDp = 600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibrarySwitcherWideDialog() {
    Shot {
        Box(Modifier.fillMaxSize().background(OmnibusTheme.palette.bg0), contentAlignment = Alignment.Center) {
            LibrarySwitcherDialogCard(libraries, onLibraryClick = {})
        }
    }
}
