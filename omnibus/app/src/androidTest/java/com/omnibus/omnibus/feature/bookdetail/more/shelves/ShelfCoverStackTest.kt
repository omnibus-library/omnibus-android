package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShelfCoverStackTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()
    private val covers = listOf("c1", "c2", "c3", "c4", "c5")
    private val rowId = 1L

    private fun setStack(on: MutableState<Boolean>, others: List<String>, reducedMotion: Boolean) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShelfCoverStack(
                    rowId = rowId,
                    coverUuids = others,
                    isOn = on.value,
                    bookCover = book.coverIdentity(),
                    reducedMotion = reducedMotion,
                )
            }
        }
    }

    /** Covers and this book's cover are decorative, so they sit under a cleared semantics node. */
    private fun ComposeContentTestRule.tagged(tag: String) = onAllNodesWithTag(tag, useUnmergedTree = true)

    private fun ComposeContentTestRule.bookCoverBounds() =
        onNodeWithTag(ShelfSheetTestTags.bookCover(rowId), useUnmergedTree = true).bounds()

    // TC-56
    @Test
    fun issue116_tc56_stackShowsThreeCoversAndThisBookOnTopWhenOn() {
        val on = mutableStateOf(false)
        setStack(on, covers, reducedMotion = true)

        rule.tagged(ShelfSheetTestTags.cover(rowId, 0)).assertCountEquals(1)
        rule.tagged(ShelfSheetTestTags.cover(rowId, 2)).assertCountEquals(1)
        rule.tagged(ShelfSheetTestTags.cover(rowId, 3)).assertCountEquals(0)
        rule.tagged(ShelfSheetTestTags.bookCover(rowId)).assertCountEquals(0)

        rule.runOnIdle { on.value = true }
        rule.waitForIdle()
        rule.tagged(ShelfSheetTestTags.cover(rowId, 1)).assertCountEquals(1)
        rule.tagged(ShelfSheetTestTags.cover(rowId, 2)).assertCountEquals(0)
        rule.tagged(ShelfSheetTestTags.bookCover(rowId)).assertCountEquals(1)

        val bookCover = rule.onNodeWithTag(ShelfSheetTestTags.bookCover(rowId), useUnmergedTree = true).bounds()
        val lastOther = rule.onNodeWithTag(ShelfSheetTestTags.cover(rowId, 1), useUnmergedTree = true).bounds()
        assertTrue("this book's cover is the rightmost", bookCover.left > lastOther.left)

        val density = rule.density
        assertEquals(with(density) { 30.dp.toPx() }, bookCover.width, 1f)
        assertEquals(with(density) { 45.dp.toPx() }, bookCover.height, 1f)
        val stack = rule.onNodeWithTag(ShelfSheetTestTags.stack(rowId), useUnmergedTree = true).bounds()
        assertTrue("stack no wider than 66 dp", stack.width <= with(density) { 66.dp.toPx() } + 1f)
    }

    // TC-56
    @Test
    fun issue116_tc56_emptyStackHasNoCovers() {
        setStack(mutableStateOf(false), emptyList(), reducedMotion = true)

        rule.onNodeWithTag(ShelfSheetTestTags.stack(rowId), useUnmergedTree = true).assertIsDisplayed()
        rule.tagged(ShelfSheetTestTags.cover(rowId, 0)).assertCountEquals(0)
    }

    // TC-56
    @Test
    fun issue116_tc56_singleCoverShowsOneOtherAndThisBookWhenOn() {
        setStack(mutableStateOf(true), listOf("c1"), reducedMotion = true)

        rule.tagged(ShelfSheetTestTags.cover(rowId, 0)).assertCountEquals(1)
        rule.tagged(ShelfSheetTestTags.cover(rowId, 1)).assertCountEquals(0)
        rule.tagged(ShelfSheetTestTags.bookCover(rowId)).assertCountEquals(1)
    }

    // TC-57
    @Test
    fun issue116_tc57_bookCoverPopsInWhenTurnedOn() {
        val on = mutableStateOf(false)
        setStack(on, covers, reducedMotion = false)
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { on.value = true }
        rule.mainClock.advanceTimeByFrame()
        val start = rule.bookCoverBounds()
        rule.mainClock.advanceTimeBy(600)
        val end = rule.bookCoverBounds()

        assertTrue("starts ${start.top} above ${end.top}", start.top < end.top - 1f)
        rule.mainClock.advanceTimeBy(600)
        assertEquals(end.top, rule.bookCoverBounds().top, 1f)
    }

    // TC-57
    @Test
    fun issue116_tc57_bookCoverDoesNotPopWhenTheShelfAlreadyHoldsTheBook() {
        setStack(mutableStateOf(true), covers, reducedMotion = false)
        rule.mainClock.autoAdvance = false

        rule.mainClock.advanceTimeByFrame()
        val start = rule.bookCoverBounds()
        rule.mainClock.advanceTimeBy(600)
        assertEquals(start.top, rule.bookCoverBounds().top, 1f)
    }

    // TC-57
    @Test
    fun issue116_tc57_bookCoverLeavesWhenTurnedOff() {
        val on = mutableStateOf(true)
        setStack(on, covers, reducedMotion = false)
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { on.value = false }
        rule.mainClock.advanceTimeBy(600)
        rule.tagged(ShelfSheetTestTags.bookCover(rowId)).assertCountEquals(0)
    }

    // TC-58
    @Test
    fun issue116_tc58_popInIsSkippedWithReducedMotion() {
        val on = mutableStateOf(false)
        setStack(on, covers, reducedMotion = true)
        rule.mainClock.autoAdvance = false

        rule.runOnIdle { on.value = true }
        rule.mainClock.advanceTimeByFrame()
        rule.tagged(ShelfSheetTestTags.bookCover(rowId)).assertCountEquals(1)
        val first = rule.bookCoverBounds()
        rule.mainClock.advanceTimeBy(600)
        assertEquals(first.top, rule.bookCoverBounds().top, 1f)
    }
}
