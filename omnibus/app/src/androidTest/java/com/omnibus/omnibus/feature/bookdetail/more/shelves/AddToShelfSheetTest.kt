package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.more.manualShelfRow
import com.omnibus.omnibus.feature.bookdetail.more.smartShelfRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Retry, closing, toggling and the smart rows' semantics. AddToShelfSheetScreenshotTest covers how the sheet looks. */
class AddToShelfSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()

    /** Renders the sheet body from [content], which the test changes; reduced motion keeps these tests still. */
    private fun setSheet(
        content: MutableState<ShelfSheetContent>,
        onToggle: (Long) -> Unit = {},
        onRetry: () -> Unit = {},
        onClose: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddToShelfSheetContent(
                    content = content.value,
                    book = book,
                    onToggle = onToggle,
                    onRetry = onRetry,
                    onClose = onClose,
                    reducedMotion = true,
                )
            }
        }
    }

    private fun ShelfSheetContent.Loaded.toggled(id: Long) = copy(
        manual = manual.map {
            if (it.id != id) {
                it
            } else {
                it.copy(
                    isOn = !it.isOn,
                    bookCount = if (it.isOn) it.bookCount - 1 else it.bookCount + 1,
                )
            }
        },
    )

    // TC-41
    @Test
    fun issue116_tc41_retryInTheErrorStateRetries() {
        var retries = 0
        setSheet(mutableStateOf(ShelfSheetContent.Failed), onRetry = { retries++ })

        rule.onNodeWithText(detailText(R.string.book_detail_more_shelf_retry)).performClick()
        rule.waitForIdle()

        assertEquals(1, retries)
    }

    // TC-43
    @Test
    fun issue116_tc43_closeButtonCallsOnClose() {
        var closes = 0
        setSheet(mutableStateOf(ShelfSheetContent.Loaded(listOf(manualShelfRow(1)), emptyList())), onClose = { closes++ })

        rule.onNodeWithTag(ShelfSheetTestTags.CLOSE).performClick()
        rule.waitForIdle()

        assertEquals(1, closes)
    }

    // TC-44
    @Test
    fun issue116_tc44_tappingAnywhereOnARowRequestsItsToggle() {
        val content = mutableStateOf<ShelfSheetContent>(
            ShelfSheetContent.Loaded(listOf(manualShelfRow(1, "One"), manualShelfRow(2, "Two")), emptyList()),
        )
        val toggled = mutableListOf<Long>()
        setSheet(content, onToggle = { id ->
            toggled += id
            content.value = (content.value as ShelfSheetContent.Loaded).toggled(id)
        })

        rule.onNodeWithTag(ShelfSheetTestTags.row(1)).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ShelfSheetTestTags.row(1)).performTouchInput { click(Offset(2f, centerY)) }
        rule.waitForIdle()

        assertEquals(listOf(1L, 1L), toggled)
        rule.onNodeWithTag(ShelfSheetTestTags.row(2))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    // TC-45
    @Test
    fun issue116_tc45_aPendingRowRendersItsStateAndStillReportsTaps() {
        val toggled = mutableListOf<Long>()
        setSheet(
            mutableStateOf(
                ShelfSheetContent.Loaded(
                    listOf(manualShelfRow(1, "Pending", count = 4, isOn = true, isPending = true)),
                    emptyList(),
                ),
            ),
            onToggle = { toggled += it },
        )

        rule.onNodeWithTag(ShelfSheetTestTags.row(1)).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        rule.onNodeWithTag(ShelfSheetTestTags.row(1)).performClick()
        rule.waitForIdle()
        assertEquals(listOf(1L), toggled)
    }

    // TC-48
    @Test
    fun issue116_tc48_smartRowsHaveNoToggleAndNoClick() {
        val toggled = mutableListOf<Long>()
        setSheet(
            mutableStateOf(
                ShelfSheetContent.Loaded(
                    manual = listOf(manualShelfRow(1, "Shelf")),
                    smart = listOf(smartShelfRow(10, "Smart", count = 2)),
                ),
            ),
            onToggle = { toggled += it },
        )

        val smart = rule.onNodeWithTag(ShelfSheetTestTags.smartRow(10))
        smart.assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.ToggleableState))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
        smart.performClick()
        rule.waitForIdle()
        assertTrue("smart row tap reported $toggled", toggled.isEmpty())
    }
}
