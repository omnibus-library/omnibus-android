package com.omnibus.omnibus.feature.libraries.manage

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.switcher.LibraryRowTestTags
import com.omnibus.omnibus.feature.libraries.switcher.LibrarySwitcherLayout
import com.omnibus.omnibus.feature.libraries.switcher.LibrarySwitcherTestTags
import com.omnibus.omnibus.feature.libraries.switcher.closeOnCurrent
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibrarySwitcherManageActionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun library(id: String, name: String, address: String, username: String?, addedAt: Long, isActive: Boolean) =
        Library(id, address, name, username, addedAt, lastUsedAt = addedAt, isActive = isActive)

    private val three = listOf(
        library("home", "Home", "https://books.home.lan", "hoon", 1, isActive = true),
        library("work", "Work book club", "https://library.acme.io", "hjames", 2, isActive = false),
        library("bare", "", "http://10.0.0.40:3000", null, 3, isActive = false),
    )

    private fun showSheet(calls: MutableList<String>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                LibrarySwitcherLayout(
                    libraries = three,
                    onLibraryClick = ::closeOnCurrent,
                    onDismiss = { calls += "dismiss" },
                    centered = false,
                    onManageLibraries = { calls += "manage" },
                )
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LibrarySwitcherTestTags.TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // TC-36 (R2, R13)
    @Test
    fun issue368_tc36_theActionSitsBelowTheLastRow() {
        showSheet(mutableListOf())

        val last = rule.onNodeWithTag(LibraryRowTestTags.row("bare")).getUnclippedBoundsInRoot()
        val manage = rule.onNodeWithTag(LibrarySwitcherTestTags.MANAGE).getUnclippedBoundsInRoot()
        assertTrue("The action should sit below the last library row", last.bottom <= manage.top)
    }

    @Test
    fun issue368_tc36_theActionIsOneButtonAtLeast48dpTall() {
        showSheet(mutableListOf())

        rule.onNodeWithTag(LibrarySwitcherTestTags.MANAGE)
            .assertTextEquals("Manage libraries")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue368_tc36_tappingItClosesTheSheetThenOpensManageLibraries() {
        val calls = mutableListOf<String>()
        showSheet(calls)

        rule.onNodeWithTag(LibrarySwitcherTestTags.MANAGE).performClick()

        rule.waitUntil(timeoutMillis = 5_000) { calls.size == 2 }
        assertEquals(listOf("dismiss", "manage"), calls)
    }
}
