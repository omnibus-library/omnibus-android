package com.omnibus.omnibus.feature.bookdetail.more.bookmarks

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class BookmarksSheetAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val rows = listOf(
        rowModel(bookmark(1, title = "The Episode of the Barrel"), BookmarkPlacement(7, 47)),
        rowModel(bookmark(2, title = "Second", createdAtSeconds = null), BookmarkPlacement(null, null)),
    )

    // TC-61
    @Test
    fun issue117_tc61_rowIsOneButtonWithACombinedDescription() {
        rule.setSheetContent(BookmarksContent.Loaded(rows))

        val row = rule.onNodeWithTag(BookmarksTestTags.row("id:1"))
        row.assertIsDisplayed()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assertContentDescriptionEquals("The Episode of the Barrel, Chapter 7, 47%, October 28")
        row.assert(isFocusable())

        rule.onNodeWithTag(BookmarksTestTags.ribbon("id:1"), useUnmergedTree = true)
            .assertHasNoContentDescription()
        rule.onNodeWithTag(BookmarksTestTags.chevron("id:1"), useUnmergedTree = true)
            .assertHasNoContentDescription()
    }

    // TC-62
    @Test
    fun issue117_tc62_rowsMeetTheTouchTargetHeight() {
        rule.setSheetContent(BookmarksContent.Loaded(rows))

        rule.onNodeWithTag(BookmarksTestTags.row("id:1")).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(BookmarksTestTags.row("id:2")).assertHeightIsAtLeast(48.dp)
    }
}

/** Asserts that the node has no content description, so a decorative element stays silent to TalkBack. */
private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertHasNoContentDescription() {
    val config = fetchSemanticsNode().config
    assertFalse(
        "decorative node must not expose a content description",
        config.contains(SemanticsProperties.ContentDescription),
    )
}
