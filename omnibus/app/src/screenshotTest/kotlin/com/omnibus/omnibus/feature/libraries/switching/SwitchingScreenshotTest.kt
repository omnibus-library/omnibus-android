package com.omnibus.omnibus.feature.libraries.switching

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
import com.omnibus.omnibus.screenshot.Shot

// Fixed libraries, not random: the screenshots must be stable.
private val home = Library(
    id = "home",
    address = "https://books.home.lan",
    name = "Home",
    username = "hoon",
    addedAt = 1,
    lastUsedAt = 1,
    isActive = true,
)

private val work = Library(
    id = "work",
    address = "https://library.acme.io",
    name = "Work book club",
    username = "hjames",
    addedAt = 2,
    lastUsedAt = 2,
    isActive = false,
)

/** The shelf of the two libraries above, with [raised] raised and nothing moving. */
private fun shelf(raised: String) = BookcaseState(
    spines = listOf(home, work).map { BookcaseSpine(it.id, monogramOf(it.displayName()), SpineHeights.of(it.id)) },
    raisedLibraryId = raised,
    transition = BookcaseTransition.Still,
)

/** The syncing stage: the current library, its monogram and "Syncing Home". */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SwitchingSyncing() {
    Shot {
        SwitchingScreenContent(
            uiState = SwitchingUIState(
                stage = SwitchingStage.Syncing,
                current = home,
                target = work,
                bookcase = shelf(raised = home.id),
            ),
            onStay = {},
            onSwitchAnyway = {},
        )
    }
}

/** The sync failed in place: the title, the body, and both choices over the still-current library. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Wide", widthDp = 840, heightDp = 600)
@Composable
fun SwitchingFailed() {
    Shot {
        SwitchingScreenContent(
            uiState = SwitchingUIState(
                stage = SwitchingStage.SyncFailed,
                current = home,
                target = work,
                bookcase = shelf(raised = home.id),
            ),
            onStay = {},
            onSwitchAnyway = {},
        )
    }
}

/** The opening stage: the new library's name and monogram, and "Opening Work book club". */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun SwitchingOpening() {
    Shot {
        SwitchingScreenContent(
            uiState = SwitchingUIState(
                stage = SwitchingStage.Opening,
                current = home,
                target = work,
                bookcase = shelf(raised = work.id),
            ),
            onStay = {},
            onSwitchAnyway = {},
        )
    }
}
