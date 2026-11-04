package com.omnibus.omnibus.feature.audiobook.bookmarks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.bookmarks.Bookmark
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarkRowModel
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksContent
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class PlayerBookmarksSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val title get() = context.getString(R.string.book_detail_bookmarks_title)
    private val closeLabel get() = context.getString(R.string.book_detail_bookmarks_close)
    private val emptyCopy get() = context.getString(R.string.book_detail_bookmarks_empty)
    private val loadingCopy get() = context.getString(R.string.book_detail_bookmarks_loading)
    private val errorCopy get() = context.getString(R.string.book_detail_bookmarks_error)
    private val retryLabel get() = context.getString(R.string.book_detail_bookmarks_retry)
    private val swipeHint get() = context.getString(R.string.audiobook_bookmarks_swipe_hint)
    private val removeLabel get() = context.getString(R.string.audiobook_bookmarks_remove)

    /** Performs the custom accessibility action [label] on the node with [tag], as TalkBack would. */
    private fun ComposeContentTestRule.performCustomAction(tag: String, label: String) {
        val node = onNodeWithTag(tag).fetchSemanticsNode()
        val action = node.config.getOrNull(SemanticsActions.CustomActions)
            ?.single { it.label == label }
        checkNotNull(action) { "No custom action \"$label\" on $tag" }
        runOnIdle { action.action() }
    }

    // TC48, R3, P-BMK-2, AND-UI-2: the title is a heading and the close button is labelled
    @Test
    fun titleIsHeadingAndCloseHasLabel() {
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()))

        rule.onNodeWithText(title).assertIsDisplayed()
        rule.onNodeWithText(title).assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        rule.onNodeWithContentDescription(closeLabel).assertIsDisplayed().assertHasClickAction()
    }

    // TC49, R3: tapping the close button reports the close once
    @Test
    fun closeButtonInvokesOnClose() {
        var closes = 0
        rule.setSheetContent(BookmarksContent.Loaded(emptyList()), onClose = { closes++ })

        rule.onNodeWithTag(PlayerBookmarksTestTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(1, closes)
    }

    // TC50, R4, AND-RUSE-6: a titled row keeps its title, an audio row reads as listening, a text row as saved position
    @Test
    fun rowsAreNamedByTitleListeningOrSavedPosition() {
        rule.setSheetContent(
            BookmarksContent.Loaded(
                rowsOf(
                    bookmark(1, title = "Fav"),
                    bookmark(2, position = "3725"),
                    bookmark(3),
                ),
            ),
        )

        rule.onNodeWithTag(BookmarksTestTags.name("id:1"), useUnmergedTree = true).assertTextEquals("Fav")
        rule.onNodeWithTag(BookmarksTestTags.name("id:2"), useUnmergedTree = true)
            .assertTextEquals("Listening, 1 h 02 m in")
        rule.onNodeWithTag(BookmarksTestTags.name("id:3"), useUnmergedTree = true)
            .assertTextEquals("Saved position")
    }

    // TC51, R4: the meta line is the date alone, with no chapter or percent
    @Test
    fun rowMetaIsDateOnly() {
        rule.setSheetContent(BookmarksContent.Loaded(rowsOf(bookmark(1), bookmark(2, position = "3725"))))

        rule.onNodeWithTag(BookmarksTestTags.meta("id:1"), useUnmergedTree = true).assertTextEquals(SAMPLE_DATE)
        rule.onNodeWithTag(BookmarksTestTags.meta("id:2"), useUnmergedTree = true).assertTextEquals(SAMPLE_DATE)
    }

    // TC52, R4: rows keep the repository's order, optimistic rows included
    @Test
    fun rowsKeepRepositoryOrder() {
        val optimistic = Bookmark(null, "client-1", "u", "120", null, null, null, null)
        rule.setSheetContent(BookmarksContent.Loaded(rowsOf(bookmark(1), optimistic, bookmark(3))))

        val keys = listOf("id:1", "client-1", "id:3")
        val tops = keys.map { rule.onNodeWithTag(BookmarksTestTags.row(it)).fetchSemanticsNode().boundsInRoot.top }
        assertTrue("rows in the repository's order", tops[0] < tops[1] && tops[1] < tops[2])
    }

    // TC53, R4, P-BMK-3: loading, failed and empty states show their copy, and none has a footer
    @Test
    fun loadingFailedAndEmptyStates() {
        val content = mutableStateOf<BookmarksContent>(BookmarksContent.Loading)
        rule.setSheetHost(content)

        rule.onNodeWithTag(PlayerBookmarksTestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithText(loadingCopy).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerBookmarksTestTags.FOOTER).assertCountEquals(0)

        rule.runOnIdle { content.value = BookmarksContent.Failed }
        rule.onNodeWithText(errorCopy).assertIsDisplayed()
        rule.onNodeWithText(retryLabel).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerBookmarksTestTags.FOOTER).assertCountEquals(0)

        rule.runOnIdle { content.value = BookmarksContent.Loaded(emptyList()) }
        rule.onNodeWithText(emptyCopy).assertIsDisplayed()
        rule.onAllNodesWithTag(PlayerBookmarksTestTags.FOOTER).assertCountEquals(0)
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

    // TC58, R6, P-BMK-2: the footer shows under a non-empty list only
    @Test
    fun footerShowsOnlyWithRows() {
        val content = mutableStateOf<BookmarksContent>(BookmarksContent.Loaded(rowsOf(bookmark(1), bookmark(2))))
        rule.setSheetHost(content)

        rule.onNodeWithTag(PlayerBookmarksTestTags.LIST).performScrollToNode(hasTestTag(PlayerBookmarksTestTags.FOOTER))
        rule.onNodeWithTag(PlayerBookmarksTestTags.FOOTER).assertIsDisplayed()
        rule.onNodeWithText(swipeHint).assertIsDisplayed()

        rule.runOnIdle { content.value = BookmarksContent.Loaded(emptyList()) }
        rule.onAllNodesWithTag(PlayerBookmarksTestTags.FOOTER).assertCountEquals(0)

        rule.runOnIdle { content.value = BookmarksContent.Loading }
        rule.onAllNodesWithTag(PlayerBookmarksTestTags.FOOTER).assertCountEquals(0)

        rule.runOnIdle { content.value = BookmarksContent.Failed }
        rule.onAllNodesWithTag(PlayerBookmarksTestTags.FOOTER).assertCountEquals(0)
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

    // TC60, R4, P-A11Y-5: the row stays a button named by its name and date with the action added
    @Test
    fun rowKeepsButtonRoleAndDescription() {
        rule.setSheetContent(BookmarksContent.Loaded(rowsOf(bookmark(2, position = "3725"))))

        val row = rule.onNodeWithTag(BookmarksTestTags.row("id:2"))
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assert(hasContentDescription("Listening, 1 h 02 m in, $SAMPLE_DATE"))
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

    // TC62, R3, R4: a long list scrolls, and its footer is reachable
    @Test
    fun longListScrollsToFooter() {
        val many = rowsOf(*Array(40) { bookmark(it + 1L) })
        rule.setSheetContent(BookmarksContent.Loaded(many))

        rule.onNodeWithTag(PlayerBookmarksTestTags.LIST).performScrollToNode(hasTestTag(PlayerBookmarksTestTags.FOOTER))
        rule.onNodeWithTag(PlayerBookmarksTestTags.FOOTER).assertIsDisplayed()
    }
}
