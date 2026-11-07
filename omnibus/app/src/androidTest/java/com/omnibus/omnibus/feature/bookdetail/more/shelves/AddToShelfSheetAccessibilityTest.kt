package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
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

class AddToShelfSheetAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()

    private fun setSheet(content: MutableState<ShelfSheetContent>, onToggle: (Long) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddToShelfSheetContent(
                    content = content.value,
                    book = book,
                    onToggle = onToggle,
                    onRetry = {},
                    onClose = {},
                    reducedMotion = true,
                )
            }
        }
    }

    private fun ShelfSheetContent.Loaded.toggled(id: Long) = copy(
        manual = manual.map {
            if (it.id != id) it else it.copy(isOn = !it.isOn, bookCount = if (it.isOn) it.bookCount - 1 else it.bookCount + 1)
        },
    )

    // TC-60
    @Test
    fun issue116_tc60_manualRowIsOneCheckboxWithNameCountStateAndActionLabel() {
        val content = mutableStateOf<ShelfSheetContent>(
            ShelfSheetContent.Loaded(listOf(manualShelfRow(1, "Favourites", count = 9)), emptyList()),
        )
        setSheet(content, onToggle = { content.value = (content.value as ShelfSheetContent.Loaded).toggled(it) })

        val row = rule.onNodeWithTag(ShelfSheetTestTags.row(1))
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        val text = row.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(" ") { it.text }
        assertTrue("merged text was '$text'", text.contains("Favourites") && text.contains(countText(9)))
        assertEquals(
            detailText(R.string.book_detail_more_shelf_add_action),
            row.fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )

        row.performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ShelfSheetTestTags.row(1))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
        assertEquals(
            detailText(R.string.book_detail_more_shelf_remove_action),
            rule.onNodeWithTag(ShelfSheetTestTags.row(1)).fetchSemanticsNode().config[SemanticsActions.OnClick].label,
        )

        // Nothing under the row is focusable or clickable on its own; the row is the one node.
        rule.onAllNodes(hasAnyAncestor(hasTestTag(ShelfSheetTestTags.row(1))) and hasClickAction(), useUnmergedTree = true)
            .assertCountEquals(0)
    }

    // TC-62
    @Test
    fun issue116_tc62_titlesAreHeadingsAndCloseIsLabelledAndCoverStacksAreHidden() {
        setSheet(
            mutableStateOf(
                ShelfSheetContent.Loaded(
                    manual = listOf(manualShelfRow(1, "Shelf", count = 2, covers = listOf("a", "b", "c"))),
                    smart = listOf(smartShelfRow(10, "Smart", count = 4, covers = listOf("d", "e"))),
                ),
            ),
        )

        rule.onNodeWithTag(ShelfSheetTestTags.TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(ShelfSheetTestTags.SMART_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(ShelfSheetTestTags.CLOSE)
            .assert(hasContentDescription(detailText(R.string.book_detail_more_shelf_close)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))

        rule.onAllNodes(
            hasAnyAncestor(hasTestTag(ShelfSheetTestTags.stack(1))) and
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }
}
