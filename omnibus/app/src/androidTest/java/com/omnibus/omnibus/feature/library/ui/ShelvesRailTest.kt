package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescriptionExactly
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Semantics, snapping and edge fades of the shelves rail. ShelvesRailScreenshotTest covers how the cards look. */
class ShelvesRailTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue233_tc19_eachCardIsOneNodeWithTheFullDescription() {
        val shelves = listOf(
            shelf(1L, ownerId = 8L, ownerName = "Ann", kind = ShelfKind.Smart, visibility = ShelfVisibility.Public, bookCount = 5L),
            shelf(2L, ownerId = 7L, kind = ShelfKind.Wishlist, visibility = ShelfVisibility.Private, bookCount = 3L),
            shelf(3L, ownerId = 7L, kind = ShelfKind.Manual, visibility = ShelfVisibility.Private, bookCount = 2L),
        )
        setRail(shelves, viewerId = 7L, fraction = 1f)
        rule.waitForIdle()

        val expected = listOf(
            "Shelf 1, 5 books, Smart shelf, Public, by Ann",
            "Shelf 2, 3 books, Wishlist",
            "Shelf 3, 2 books, Shelf",
        )
        shelves.forEachIndexed { index, preview ->
            val id = preview.shelf.id
            // The row only composes the cards on screen, and three cards are wider than a 320 dp window.
            rule.onNodeWithTag(ShelvesRailTestTags.ROW).performScrollToIndex(index)
            rule.onNodeWithTag(ShelvesRailTestTags.card(id)).assert(hasContentDescriptionExactly(expected[index]))
            // The card's text is merged into its one node, so nothing under the card is a node of its own.
            rule.onAllNodes(hasAnyAncestor(hasTestTag(ShelvesRailTestTags.card(id)))).assertCountEquals(0)
        }
    }

    @Test
    fun issue233_tc20_aCardHasNoClickAction() {
        setRail(listOf(shelf(1L)), viewerId = 7L, fraction = 1f)
        rule.waitForIdle()

        rule.onNodeWithTag(ShelvesRailTestTags.card(1L))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
    }

    @Test
    fun issue233_tc21_theRowSnapsACardToTheStartPadding() {
        setRail(eightShelves(), viewerId = 1L, fraction = 0.5f, state = LazyListState())
        rule.waitForIdle()
        val restLeft = leftOf(1L)!!

        rule.onNodeWithTag(ShelvesRailTestTags.ROW).performTouchInput {
            swipeLeft(startX = right * 0.9f, endX = right * 0.4f, durationMillis = 600)
        }
        rule.waitForIdle()

        // Card 1 is the first card, so it is left out: it would mean nothing moved.
        val snapped = (2L..8L).any { id -> leftOf(id)?.let { abs(it - restLeft) <= 1f } == true }
        assertTrue("a card other than the first now rests at the start padding", snapped)
    }

    @Test
    fun issue233_tc22_fadesShowOnlyWhereTheRowCanScroll() {
        val state = LazyListState()
        setRail(eightShelves(), viewerId = 1L, fraction = 0.5f, state = state)
        rule.waitForIdle()

        assertFadeShown(ShelvesRailTestTags.START_FADE, shown = false)
        assertFadeShown(ShelvesRailTestTags.END_FADE, shown = true)

        rule.onNodeWithTag(ShelvesRailTestTags.ROW).performScrollToIndex(1)
        rule.waitForIdle()

        assertTrue("the row moved off its first card", state.firstVisibleItemIndex == 1)
        assertFadeShown(ShelvesRailTestTags.START_FADE, shown = true)
        assertFadeShown(ShelvesRailTestTags.END_FADE, shown = true)

        rule.onNodeWithTag(ShelvesRailTestTags.ROW).performScrollToIndex(7)
        rule.waitForIdle()

        assertTrue("the row moved to its last card", state.firstVisibleItemIndex == 7)
        assertFadeShown(ShelvesRailTestTags.START_FADE, shown = true)
        assertFadeShown(ShelvesRailTestTags.END_FADE, shown = false)
    }

    @Test
    fun issue233_tc23_aRowThatFitsShowsNoFade() {
        setRail(listOf(shelf(1L)), viewerId = 7L, fraction = 1f)
        rule.waitForIdle()

        assertFadeShown(ShelvesRailTestTags.START_FADE, shown = false)
        assertFadeShown(ShelvesRailTestTags.END_FADE, shown = false)
    }

    private fun setRail(
        shelves: List<ShelfPreview>,
        viewerId: Long?,
        fraction: Float,
        state: LazyListState = LazyListState(),
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(fraction)) {
                    ShelvesRail(shelves = shelves, viewerId = viewerId, state = state)
                }
            }
        }
    }

    private fun assertFadeShown(tag: String, shown: Boolean) {
        rule.onNodeWithTag(tag, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(ShelvesRailFadeShownKey, shown))
    }

    /** The left edge of the card for [id] in the window, or null while the row hasn't composed it. */
    private fun leftOf(id: Long): Float? = rule.onAllNodesWithTag(ShelvesRailTestTags.card(id), useUnmergedTree = true)
        .fetchSemanticsNodes()
        .firstOrNull()
        ?.boundsInRoot
        ?.left

    /** Eight of the viewer's own manual shelves with no covers, so each card is a plate. */
    private fun eightShelves(): List<ShelfPreview> = (1L..8L).map { shelf(it) }

    private fun shelf(
        id: Long,
        ownerId: Long = 7L,
        ownerName: String = "Hunter",
        kind: ShelfKind = ShelfKind.Manual,
        visibility: ShelfVisibility = ShelfVisibility.Private,
        bookCount: Long = 1L,
    ) = ShelfPreview(
        shelf = Shelf(
            id = id,
            ownerUserId = ownerId,
            ownerUsername = ownerName,
            kind = kind,
            name = "Shelf $id",
            visibility = visibility,
            accent = null,
            bookCount = bookCount,
        ),
        coverUuids = emptyList(),
    )
}
