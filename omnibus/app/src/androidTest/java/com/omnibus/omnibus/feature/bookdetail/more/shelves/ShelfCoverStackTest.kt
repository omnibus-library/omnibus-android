package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/** The book cover leaving its stack. ShelfCoverStackScreenshotTest covers how each stack looks. */
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
}
