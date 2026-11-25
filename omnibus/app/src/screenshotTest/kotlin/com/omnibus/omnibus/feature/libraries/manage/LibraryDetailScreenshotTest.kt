package com.omnibus.omnibus.feature.libraries.manage

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.screenshot.Shot

// A fixed library, not random: the screenshots must be stable.
private val home = Library(
    id = "home",
    address = "https://books.home.lan",
    name = "Home",
    username = "hoon",
    addedAt = 1,
    lastUsedAt = 1,
    isActive = true,
)

/** The detail on a phone, and from 600dp wide, where its content is capped at 560dp and centred. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Wide", widthDp = 840, heightDp = 600)
@Composable
fun LibraryDetail() {
    Shot {
        LibraryDetailScreenContent(
            uiState = LibraryDetailUIState(library = home, name = "Home"),
            onNameChange = {},
            onLeave = {},
            onBack = {},
        )
    }
}

/** The largest font on a narrow window with a long address: nothing clips. A preview can't change its content, so it is its own function. */
@PreviewTest
@Preview(name = "LargeFontNarrow", widthDp = 320, heightDp = 640, fontScale = 2f)
@Composable
fun LibraryDetailLargeFont() {
    Shot {
        LibraryDetailScreenContent(
            uiState = LibraryDetailUIState(
                library = home.copy(address = "https://a-very-long-library-hostname.example.org:8443"),
                name = "Home",
            ),
            onNameChange = {},
            onLeave = {},
            onBack = {},
        )
    }
}
