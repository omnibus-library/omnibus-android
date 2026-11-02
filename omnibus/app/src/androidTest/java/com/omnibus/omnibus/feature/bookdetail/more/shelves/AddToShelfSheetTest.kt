package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.more.countText
import com.omnibus.omnibus.feature.bookdetail.more.manualShelfRow
import com.omnibus.omnibus.feature.bookdetail.more.smartShelfRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AddToShelfSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()
    private val title get() = detailText(R.string.book_detail_more_add_to_shelf)
    private val intro get() = detailText(R.string.book_detail_more_shelf_intro)
    private val smartNote get() = detailText(R.string.book_detail_more_shelf_smart_note)

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
    fun issue116_tc41_loadingAndErrorShowTheirStatesAndRetry() {
        val content = mutableStateOf<ShelfSheetContent>(ShelfSheetContent.Loading)
        var retries = 0
        setSheet(content, onRetry = { retries++ })

        rule.onNodeWithTag(ShelfSheetTestTags.LOADING).assertIsDisplayed()
        rule.onNodeWithTag(ShelfSheetTestTags.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(ShelfSheetTestTags.CLOSE).assertIsDisplayed()

        rule.runOnIdle { content.value = ShelfSheetContent.Failed }
        rule.waitForIdle()
        rule.onNodeWithTag(ShelfSheetTestTags.ERROR).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_more_shelf_retry)).performClick()
        rule.waitForIdle()
        assertEquals(1, retries)
    }

    // TC-42
    @Test
    fun issue116_tc42_listsManualShelvesInOrderWithPluralCounts() {
        val rows = listOf(
            manualShelfRow(1, "Currently reading", count = 1),
            manualShelfRow(2, "Empty shelf", count = 0),
            manualShelfRow(3, "Favourites", count = 12, isOn = true),
        )
        setSheet(mutableStateOf(ShelfSheetContent.Loaded(rows, emptyList())))

        rule.onNodeWithTag(ShelfSheetTestTags.TITLE).assertIsDisplayed()
        rule.onNodeWithTag(ShelfSheetTestTags.INTRO).assertIsDisplayed()
        rule.onNodeWithText(intro).assertIsDisplayed()
        rule.onNodeWithTag(ShelfSheetTestTags.CLOSE).assertIsDisplayed()

        val tops = rows.map { rule.onNodeWithTag(ShelfSheetTestTags.row(it.id)).bounds().top }
        assertEquals("rows are in server order", tops.sorted(), tops)

        rule.onNodeWithText("Currently reading").assertIsDisplayed()
        rule.onNodeWithText(countText(1)).assertIsDisplayed()
        rule.onNodeWithText(countText(12)).assertIsDisplayed()
        rule.onNodeWithTag(ShelfSheetTestTags.row(1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        rule.onNodeWithTag(ShelfSheetTestTags.row(3))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    // TC-43
    @Test
    fun issue116_tc43_closeButtonCallsOnCloseAndIsLabelledAndLargeEnough() {
        var closes = 0
        setSheet(mutableStateOf(ShelfSheetContent.Loaded(listOf(manualShelfRow(1)), emptyList())), onClose = { closes++ })

        val close = rule.onNodeWithTag(ShelfSheetTestTags.CLOSE)
        close.assert(hasContentDescription(detailText(R.string.book_detail_more_shelf_close)))
        val minimum = with(rule.density) { 44.dp.toPx() }
        assertTrue("close width ${close.bounds().width}", close.bounds().width >= minimum - 1f)
        close.performClick()
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

    // TC-46
    @Test
    fun issue116_tc46_rowVisualsFollowStateOnToggleAndRevert() {
        val content = mutableStateOf<ShelfSheetContent>(
            ShelfSheetContent.Loaded(listOf(manualShelfRow(1, "Revert", count = 2, isOn = false)), emptyList()),
        )
        setSheet(content)

        rule.runOnIdle {
            content.value = ShelfSheetContent.Loaded(listOf(manualShelfRow(1, "Revert", count = 3, isOn = true)), emptyList())
        }
        rule.waitForIdle()
        rule.onNodeWithText(countText(3)).assertIsDisplayed()
        rule.onAllNodesWithTag(ShelfSheetTestTags.bookCover(1), useUnmergedTree = true).assertCountEquals(1)

        rule.runOnIdle {
            content.value = ShelfSheetContent.Loaded(listOf(manualShelfRow(1, "Revert", count = 2, isOn = false)), emptyList())
        }
        rule.waitForIdle()
        rule.onNodeWithText(countText(2)).assertIsDisplayed()
        rule.onNodeWithTag(ShelfSheetTestTags.row(1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        rule.onAllNodesWithTag(ShelfSheetTestTags.bookCover(1), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-47
    @Test
    fun issue116_tc47_smartSectionListsReadOnlyRowsWithRuleTextOrCountOnly() {
        val year = ShelfRules(
            matchMode = ShelfMatchMode.All,
            rules = listOf(ShelfRule(ShelfRuleField.Year, ShelfRuleOp.Gte, "1901")),
        )
        setSheet(
            mutableStateOf(
                ShelfSheetContent.Loaded(
                    manual = listOf(manualShelfRow(1, "Shelf", count = 5)),
                    smart = listOf(
                        smartShelfRow(10, "Old favourites", count = 14, covers = listOf("a", "b", "c"), rules = year),
                        smartShelfRow(11, "In progress", count = 3, rules = null),
                    ),
                ),
            ),
        )

        val heading = rule.onNodeWithTag(ShelfSheetTestTags.SMART_TITLE).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(smartNote).performScrollTo().assertIsDisplayed()
        assertTrue("smart heading sits below the manual row", heading.bounds().top > rule.onNodeWithTag(ShelfSheetTestTags.row(1)).bounds().bottom)

        val yearClause = detailText(
            R.string.book_detail_more_rule_clause,
            detailText(R.string.book_detail_more_rule_field_year),
            detailText(R.string.book_detail_more_rule_op_gte, "1901"),
        )
        rule.onNodeWithText(detailText(R.string.book_detail_more_shelf_meta, countText(14), yearClause))
            .performScrollTo().assertIsDisplayed()
        rule.onNodeWithText(countText(3)).performScrollTo().assertIsDisplayed()
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

    // TC-49
    @Test
    fun issue116_tc49_noSmartSectionWhenNothingMatches() {
        setSheet(mutableStateOf(ShelfSheetContent.Loaded(listOf(manualShelfRow(1, "Shelf")), emptyList())))

        rule.onNodeWithTag(ShelfSheetTestTags.INTRO).assertIsDisplayed()
        rule.onAllNodesWithTag(ShelfSheetTestTags.SMART_TITLE).assertCountEquals(0)
    }

    // TC-50
    @Test
    fun issue116_tc50_emptyStateReplacesIntroAndRowsButKeepsSmartSection() {
        val content = mutableStateOf<ShelfSheetContent>(
            ShelfSheetContent.Loaded(
                manual = emptyList(),
                smart = listOf(smartShelfRow(10, "Smart", count = 1)),
            ),
        )
        setSheet(content)

        rule.onNodeWithTag(ShelfSheetTestTags.EMPTY).assertIsDisplayed()
        rule.onNodeWithText(detailText(R.string.book_detail_more_shelf_empty)).assertIsDisplayed()
        rule.onAllNodesWithTag(ShelfSheetTestTags.INTRO).assertCountEquals(0)
        rule.onNodeWithTag(ShelfSheetTestTags.SMART_TITLE).assertIsDisplayed()

        rule.runOnIdle { content.value = ShelfSheetContent.Loaded(emptyList(), emptyList()) }
        rule.waitForIdle()
        rule.onNodeWithTag(ShelfSheetTestTags.EMPTY).assertIsDisplayed()
        rule.onAllNodesWithTag(ShelfSheetTestTags.SMART_TITLE).assertCountEquals(0)
    }
}
