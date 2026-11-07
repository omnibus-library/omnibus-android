package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Callbacks, swipes and semantics. PlayerBookmarksSheetScreenshotTest covers how the rows look. */
class PlayerBookmarksSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val title get() = context.getString(R.string.book_detail_bookmarks_title)
    private val closeLabel get() = context.getString(R.string.book_detail_bookmarks_close)
    private val retryLabel get() = context.getString(R.string.book_detail_bookmarks_retry)
    private val removeLabel get() = context.getString(R.string.audiobook_bookmarks_remove)

    /** Performs the custom accessibility action [label] on the node with [tag], as TalkBack would. */
    private fun ComposeContentTestRule.performCustomAction(tag: String, label: String) {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.CustomActions)
            ?.single { it.label == label }
        checkNotNull(action) { "No custom action \"$label\" on $tag" }
        runOnIdle { action.action() }
    }

    // TC48, R3, P-BMK-2, AND-UI-2: the title is a heading
    @Test
    fun titleIsHeading() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    // TC49, R3, AND-UI-2: tapping the labelled close button reports the close once
    @Test
    fun closeButtonInvokesOnClose() {
        var closes = 0
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()), onClose = { closes++ })

        rule.onNodeWithContentDescription(closeLabel).performClick()
        rule.waitForIdle()

        assertEquals(1, closes)
    }

    // TC54, R4: "Try again" reports a retry once
    @Test
    fun tryAgainInvokesOnRetry() {
        var retries = 0
        rule.setSheetContent(BookmarksContent.Failed, onRetry = { retries++ })

        rule.onNodeWithText(retryLabel).assertIsDisplayed().performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    // TC55, R5, AC2: a tap reports the row it chose
    @Test
    fun tappingRowReportsChosenRow() {
        val chosen = mutableListOf<String>()
        rule.setSheetContent(
            BookmarksContent.Loaded(rowsOf(bookmark(1), bookmark(2, position = "3725"))),
            onChosen = { chosen += it.key },
        )

        rule.onNodeWithTag(BookmarksTestTags.row("id:2")).performClick()
        rule.onNodeWithTag(BookmarksTestTags.row("id:1")).performClick()
        rule.waitForIdle()

        assertEquals(listOf("id:2", "id:1"), chosen)
    }

    // TC56, R6, AC2: a swipe end to start removes the row once
    @Test
    fun swipingRowToStartRemovesIt() {
        val removed = mutableListOf<String>()
        rule.setSheetContent(
            BookmarksContent.Loaded(rowsOf(bookmark(1), bookmark(2))),
            onRemove = { removed += it.key },
        )

        rule.onNodeWithTag(PlayerBookmarksTestTags.swipe("id:1")).performTouchInput { swipeLeft() }
        rule.waitForIdle()

        assertEquals(listOf("id:1"), removed)
    }

    // TC57, R6: a swipe start to end does nothing
    @Test
    fun swipingRowToEndDoesNothing() {
        val removed = mutableListOf<String>()
        rule.setSheetContent(
            BookmarksContent.Loaded(rowsOf(bookmark(1), bookmark(2))),
            onRemove = { removed += it.key },
        )

        rule.onNodeWithTag(PlayerBookmarksTestTags.swipe("id:1")).performTouchInput { swipeRight() }
        rule.waitForIdle()

        assertTrue("no removal on a start-to-end swipe", removed.isEmpty())
    }

    // TC59, P-A11Y-5, AND-RUSE-6: every row offers "Remove bookmark" as a custom action, and performing it removes that row
    @Test
    fun rowOffersRemoveCustomAction() {
        val removed = mutableListOf<String>()
        rule.setSheetContent(
            BookmarksContent.Loaded(rowsOf(bookmark(1), bookmark(2, position = "3725"))),
            onRemove = { removed += it.key },
        )

        rule.performCustomAction(BookmarksTestTags.row("id:1"), removeLabel)
        rule.performCustomAction(BookmarksTestTags.row("id:2"), removeLabel)

        assertEquals(listOf("id:1", "id:2"), removed)
    }

    // TC61, R6: a row that comes back after a failed removal can be swiped again
    @Test
    fun rolledBackRowCanBeSwipedAgain() {
        val removed = mutableListOf<String>()
        val rows = mutableStateOf(rowsOf(bookmark(1), bookmark(2)))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    PlayerBookmarksSheetContent(
                        content = BookmarksContent.Loaded(rows.value),
                        nowMillis = SHEET_NOW,
                        ribbonColor = TestRibbon,
                        onRetry = {},
                        onClose = {},
                        onChosen = {},
                        onRemove = { row ->
                            removed += row.key
                            rows.value = rows.value - row
                        },
                    )
                }
            }
        }

        rule.onNodeWithTag(PlayerBookmarksTestTags.swipe("id:1")).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf("id:1"), removed)

        rule.runOnIdle { rows.value = rowsOf(bookmark(1), bookmark(2)) }
        rule.onNodeWithTag(PlayerBookmarksTestTags.swipe("id:1")).assertIsDisplayed()

        rule.onNodeWithTag(PlayerBookmarksTestTags.swipe("id:1")).performTouchInput { swipeLeft() }
        rule.waitForIdle()
        assertEquals(listOf("id:1", "id:1"), removed)
    }
}
