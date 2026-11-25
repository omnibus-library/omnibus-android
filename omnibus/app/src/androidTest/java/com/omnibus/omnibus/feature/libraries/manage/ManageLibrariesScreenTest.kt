package com.omnibus.omnibus.feature.libraries.manage

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.libraries.Library
import com.omnibus.omnibus.feature.libraries.switcher.LibraryRowTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManageLibrariesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun library(id: String, name: String, address: String, username: String?, addedAt: Long, isActive: Boolean) =
        Library(id, address, name, username, addedAt, lastUsedAt = addedAt, isActive = isActive)

    private val home = library("home", "Home", "https://books.home.lan", "hoon", 1, isActive = true)
    private val work = library("work", "Work book club", "https://library.acme.io", "hjames", 2, isActive = false)
    private val bare = library("bare", "", "http://10.0.0.40:3000", null, 3, isActive = false)
    private val three = listOf(home, work, bare)

    private fun showList(onOpenLibrary: (String) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ManageLibrariesScreenContent(
                    libraries = three,
                    onBack = {},
                    onOpenLibrary = onOpenLibrary,
                )
            }
        }
    }

    // TC-23 (R3, R4, R13)
    @Test
    fun issue368_tc23_titleIsAHeading() {
        showList()

        rule.onNodeWithTag(ManageLibrariesTestTags.TITLE).assertTextEquals("Libraries").assert(isHeading())
    }

    @Test
    fun issue368_tc23_listsRowsInTheGivenOrder() {
        showList()

        val tops = three.map { rule.onNodeWithTag(LibraryRowTestTags.row(it.id)).getUnclippedBoundsInRoot().top }
        assertEquals("Rows should follow the order the libraries were given in", tops.sorted(), tops)
    }

    @Test
    fun issue368_tc23_eachRowIsOneButtonAtLeast48dpTall() {
        showList()

        rule.onNodeWithTag(LibraryRowTestTags.row("work"))
            .assertContentDescriptionEquals("Work book club, hjames on library.acme.io")
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue368_tc23_tappingARowOpensThatLibrary() {
        val opened = mutableListOf<String>()
        showList(onOpenLibrary = { opened += it })

        rule.onNodeWithTag(LibraryRowTestTags.row("work")).performClick()

        rule.waitUntil(timeoutMillis = 5_000) { opened.isNotEmpty() }
        assertEquals(listOf("work"), opened)
    }
}
