package com.omnibus.omnibus.feature.libraries.switching

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.displayName
import com.omnibus.omnibus.feature.libraries.monogramOf
import com.omnibus.omnibus.screenshot.Shot

// Fixed ids and names, not random: spine heights come from the ids, so the screenshots must be stable.
private val libraries = listOf(
    library(id = "home", name = "Home", addedAt = 1, isActive = true),
    library(id = "reading-group", name = "Reading group", addedAt = 2),
    library(id = "work", name = "Work book club", addedAt = 3),
    library(id = "poetry", name = "Poetry", addedAt = 4),
    library(id = "archive", name = "Archive", addedAt = 5),
)

private val home = libraries[0]
private val work = libraries[2]

private fun library(id: String, name: String, addedAt: Long, isActive: Boolean = false) = Library(
    id = id,
    address = "https://$id.lan",
    name = name,
    username = "hoon",
    addedAt = addedAt,
    lastUsedAt = addedAt,
    isActive = isActive,
)

/** The five libraries as a shelf, with [raised] raised and nothing moving: the settled end of a lift. */
private fun shelf(raised: String) = BookcaseState(
    spines = libraries.map { BookcaseSpine(it.id, monogramOf(it.displayName()), SpineHeights.of(it.id)) },
    raisedLibraryId = raised,
    transition = BookcaseTransition.Still,
)

/** Syncing: the shelf with Home raised, and "Syncing Home" under the illuminated initial. */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Wide", widthDp = 840, heightDp = 600)
@Composable
fun BookcaseSyncing() {
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

/** Opening: the shelf settled with Work book club raised, and "Opening Work book club". */
@PreviewTest
@Preview(name = "Light", widthDp = 411, heightDp = 891)
@Preview(name = "Dark", widthDp = 411, heightDp = 891, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun BookcaseOpening() {
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
