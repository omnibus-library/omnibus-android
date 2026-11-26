package com.omnibus.omnibus.feature.libraries.add

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.manage.ManageLibrariesScreenContent
import com.omnibus.omnibus.feature.libraries.manage.ManageLibrariesTestTags
import com.omnibus.omnibus.feature.libraries.switcher.LibraryRowTestTags
import com.omnibus.omnibus.feature.libraries.switcher.LibrarySwitcherContent
import com.omnibus.omnibus.feature.libraries.switcher.LibrarySwitcherTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The "Add library" action on the switcher sheet and under Manage libraries (#371). Relative asserts only. */
class AddLibraryActionTest {

    @get:Rule
    val rule = createComposeRule()

    private fun library(id: String, name: String, address: String, username: String?, addedAt: Long, isActive: Boolean) =
        Library(id, address, name, username, addedAt, lastUsedAt = addedAt, isActive = isActive)

    private val home = library("home", "Home", "https://books.home.lan", "hoon", 1, isActive = true)
    private val work = library("work", "Work book club", "https://library.acme.io", "hjames", 2, isActive = false)

    // TC-16 (R1, R15)
    @Test
    fun issue371_tc16_theSheetOffersAddLibraryAboveManage() {
        var adds = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                LibrarySwitcherContent(
                    libraries = listOf(home, work),
                    onLibraryClick = {},
                    onAddLibrary = { adds++ },
                )
            }
        }

        rule.onNodeWithTag(LibrarySwitcherTestTags.ADD)
            .assertTextEquals("Add library")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals("one tap adds one library", 1, adds)
        val addTop = rule.onNodeWithTag(LibrarySwitcherTestTags.ADD).getUnclippedBoundsInRoot().top
        val manageTop = rule.onNodeWithTag(LibrarySwitcherTestTags.MANAGE).getUnclippedBoundsInRoot().top
        assertTrue("Add library sits above Manage libraries", addTop < manageTop)
    }

    // TC-17 (R2, R15)
    @Test
    fun issue371_tc17_manageOffersAddLibraryUnderTheList() {
        var adds = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ManageLibrariesScreenContent(
                    libraries = listOf(home, work),
                    onBack = {},
                    onOpenLibrary = {},
                    footer = { AddLibraryAction(onClick = { adds++ }, testTag = ManageLibrariesTestTags.ADD) },
                )
            }
        }

        rule.onNodeWithTag(ManageLibrariesTestTags.ADD)
            .assertTextEquals("Add library")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
            .performClick()

        assertEquals("one tap adds one library", 1, adds)
        val lastRowBottom = rule.onNodeWithTag(LibraryRowTestTags.row(work.id)).getUnclippedBoundsInRoot().bottom
        val addTop = rule.onNodeWithTag(ManageLibrariesTestTags.ADD).getUnclippedBoundsInRoot().top
        assertTrue("Add library sits under the last library", addTop >= lastRowBottom)
    }
}
