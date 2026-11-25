package com.omnibus.omnibus.feature.libraries.switcher

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibrarySwitcherSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private fun library(id: String, name: String, address: String, username: String?, addedAt: Long, isActive: Boolean) =
        Library(id, address, name, username, addedAt, lastUsedAt = addedAt, isActive = isActive)

    private val home = library("home", "Home", "https://books.home.lan", "hoon", 1, isActive = true)
    private val work = library("work", "Work book club", "https://library.acme.io", "hjames", 2, isActive = false)
    private val bare = library("bare", "", "http://10.0.0.40:3000", null, 3, isActive = false)
    private val three = listOf(home, work, bare)

    private val current = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "current")
    private val notCurrent = SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription)

    private fun showSheet(libraries: List<Library>, centered: Boolean, onDismiss: () -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                LibrarySwitcherLayout(
                    libraries = libraries,
                    onLibraryClick = ::closeOnCurrent,
                    onDismiss = onDismiss,
                    centered = centered,
                )
            }
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(LibrarySwitcherTestTags.TITLE).fetchSemanticsNodes().isNotEmpty()
        }
    }

    // TC-4 (AC1, AC2, AC5)
    @Test
    fun issue367_tc4_listsLibrariesInOrderUnderTheTitle() {
        showSheet(three, centered = false)

        rule.onNodeWithTag(LibrarySwitcherTestTags.TITLE).assertTextEquals("Libraries").assert(isHeading())
        rule.onNodeWithTag(LibraryRowTestTags.row("home"))
            .assertContentDescriptionEquals("Home, hoon on books.home.lan")
            .assert(current)
        rule.onNodeWithTag(LibraryRowTestTags.row("work"))
            .assertContentDescriptionEquals("Work book club, hjames on library.acme.io")
            .assert(notCurrent)
        rule.onNodeWithTag(LibraryRowTestTags.row("bare"))
            .assertContentDescriptionEquals("10.0.0.40:3000, on 10.0.0.40:3000")
            .assert(notCurrent)

        val tops = three.map { rule.onNodeWithTag(LibraryRowTestTags.row(it.id)).getUnclippedBoundsInRoot().top }
        assertEquals("Rows should follow the order the libraries were added", tops.sorted(), tops)
    }

    // TC-5 (AC2)
    @Test
    fun issue367_tc5_aLongListScrollsToItsLastLibrary() {
        val many = (1..40).map { i ->
            library("lib$i", "Library $i", "https://lib$i.example", "reader", i.toLong(), isActive = i == 1)
        }
        showSheet(many, centered = false)

        rule.onNodeWithTag(LibrarySwitcherTestTags.LIST)
            .performScrollToNode(hasTestTag(LibraryRowTestTags.row("lib40")))
        rule.onNodeWithTag(LibraryRowTestTags.row("lib40")).assertIsDisplayed()
        rule.onNodeWithTag(LibrarySwitcherTestTags.TITLE).assertIsDisplayed()
    }

    // TC-6 (AC3)
    @Test
    fun issue367_tc6_tappingTheOpenLibraryClosesTheSheet() {
        var dismissals = 0
        showSheet(three, centered = false, onDismiss = { dismissals++ })

        rule.onNodeWithTag(LibraryRowTestTags.row("work")).performClick()
        rule.waitForIdle()
        assertEquals("Another library should not close the sheet before switching exists", 0, dismissals)

        rule.onNodeWithTag(LibraryRowTestTags.row("home")).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { dismissals == 1 }
    }

    // TC-7 (AC4)
    @Test
    fun issue367_tc7_wideWindowsGetACappedDialogThatClosesTheSameWay() {
        var dismissals = 0
        showSheet(three, centered = true, onDismiss = { dismissals++ })

        val width = rule.onNodeWithTag(LibrarySwitcherTestTags.SHEET).getUnclippedBoundsInRoot().width
        assertTrue("The dialog should be at most 560dp wide, was $width", width <= 560.dp)

        rule.onNodeWithTag(LibraryRowTestTags.row("home")).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { dismissals == 1 }
    }

    // TC-8 (AC5)
    @Test
    fun issue367_tc8_rowsAreButtonsAtLeast48dpTall() {
        showSheet(three, centered = false)

        three.forEach {
            rule.onNodeWithTag(LibraryRowTestTags.row(it.id)).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        }
    }
}
