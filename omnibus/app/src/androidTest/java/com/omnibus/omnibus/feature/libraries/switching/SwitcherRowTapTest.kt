package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.switcher.LibraryRowTestTags
import com.omnibus.omnibus.feature.libraries.switcher.LibrarySwitcherLayout
import com.omnibus.omnibus.feature.libraries.switcher.LibrarySwitcherTestTags
import com.omnibus.omnibus.feature.libraries.switcher.switchOrClose
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The switcher's row tap, wired to a switch (#369 TC-15). Relative asserts only. */
class SwitcherRowTapTest {

    @get:Rule
    val rule = createComposeRule()

    private val home = library("home", "Home", isActive = true)
    private val work = library("work", "Work book club", isActive = false)
    private val bare = library("bare", "", isActive = false)
    private val three = listOf(home, work, bare)

    /** Shows the sheet, wired as the app wires it: a row tap goes through [switchOrClose]. */
    private fun showSheet(onDismiss: () -> Unit, onSwitchTo: (String) -> Unit) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                LibrarySwitcherLayout(
                    libraries = three,
                    onLibraryClick = { library, close -> switchOrClose(library, close, onSwitchTo) },
                    onDismiss = onDismiss,
                    centered = false,
                )
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LibrarySwitcherTestTags.TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun issue369_tc15_tappingAnotherLibrarySwitches() {
        var dismissals = 0
        val switchedTo = mutableListOf<String>()
        showSheet(onDismiss = { dismissals++ }, onSwitchTo = { switchedTo += it })

        rule.onNodeWithTag(LibraryRowTestTags.row("work")).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { dismissals == 1 }

        assertEquals(listOf("work"), switchedTo)
    }

    @Test
    fun issue369_tc15_tappingTheOpenLibraryOnlyCloses() {
        var dismissals = 0
        val switchedTo = mutableListOf<String>()
        showSheet(onDismiss = { dismissals++ }, onSwitchTo = { switchedTo += it })

        rule.onNodeWithTag(LibraryRowTestTags.row("home")).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { dismissals == 1 }

        assertEquals(emptyList<String>(), switchedTo)
    }
}

/** A library with fixed fields, so the screen under test shows the same copy every run. */
private fun library(id: String, name: String, isActive: Boolean): Library = Library(
    id = id,
    address = "https://$id.example",
    name = name,
    username = null,
    addedAt = 0,
    lastUsedAt = 0,
    isActive = isActive,
)
