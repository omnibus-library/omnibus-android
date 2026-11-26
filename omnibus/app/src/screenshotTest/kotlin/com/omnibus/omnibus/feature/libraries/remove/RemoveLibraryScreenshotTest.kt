package com.omnibus.omnibus.feature.libraries.remove

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.manage.LibraryDetailScreenContent
import com.omnibus.omnibus.feature.libraries.manage.LibraryDetailUIState
import com.omnibus.omnibus.screenshot.Shot

// A fixed library, not random: the screenshots must be stable.
private val home = Library(
    id = "home",
    address = "https://books.home.lan",
    name = "Home",
    username = "hoon",
    addedAt = 1,
    lastUsedAt = 1,
    isActive = false,
)

/** The confirm dialog on a phone, dark, and from 600dp wide, where it is centred and no wider than its cap. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Wide", widthDp = 840, heightDp = 600)
@Composable
fun RemoveLibraryConfirm() {
    Shot {
        RemoveLibraryConfirmDialog(name = "Home", onCancel = {}, onRemove = {})
    }
}

/** The confirm dialog while the sync runs: Removing with a progress indicator, and Cancel disabled. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RemoveLibraryRemoving() {
    Shot {
        RemoveLibraryConfirmDialog(name = "Home", onCancel = {}, onRemove = {}, isRemoving = true)
    }
}

/** The dialog after the sync failed: "Couldn't sync Home" with Remove anyway. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun RemoveLibrarySyncFailed() {
    Shot {
        RemoveLibrarySyncFailedDialog(name = "Home", onCancel = {}, onRemoveAnyway = {})
    }
}

/** The detail of an inactive library, with Remove library under the address and username rows. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun LibraryDetailRemoveButton() {
    Shot {
        LibraryDetailScreenContent(
            uiState = LibraryDetailUIState(library = home, name = "Home"),
            onNameChange = {},
            onLeave = {},
            onBack = {},
            actions = { RemoveLibraryButton(onClick = {}) },
        )
    }
}
