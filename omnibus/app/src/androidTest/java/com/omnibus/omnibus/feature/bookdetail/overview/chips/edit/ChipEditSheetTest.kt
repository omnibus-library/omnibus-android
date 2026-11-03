package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.VocabularyEntry
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A mutable editor state for [ChipEditSheetContent], so typing in the field updates what the sheet shows. */
private class SheetHolder(initial: ChipEditUIState) {
    var value by mutableStateOf(initial)
}

private const val TITLE_BOOK = "The Left Hand of Darkness"

/** The sheet's content, rendered alone: its header, chips, field, suggestions, create row and notes. */
class ChipEditSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val chipEdit = ChipEditTestTags
    private val removed = mutableListOf<String>()
    private val added = mutableListOf<String>()
    private val typed = mutableListOf<String>()

    private fun ComposeContentTestRule.setSheet(
        initial: ChipEditUIState,
        kind: MetadataListKind = MetadataListKind.Genres,
        inlineError: String? = null,
    ): SheetHolder {
        val holder = SheetHolder(initial)
        setContent {
            OmnibusTheme(darkTheme = true) {
                ChipEditSheetContent(
                    state = holder.value,
                    kind = kind,
                    bookTitle = TITLE_BOOK,
                    inlineError = inlineError,
                    onRemove = { removed += it },
                    onAdd = { added += it },
                    onEntryChange = {
                        typed += it
                        holder.value = holder.value.copy(entry = it)
                    },
                )
            }
        }
        waitForIdle()
        return holder
    }

    private fun pool(vararg entries: Pair<String, Int>) =
        PoolState.Loaded(entries.map { (name, count) -> VocabularyEntry(name, count.toLong()) })

    // TC-79
    @Test
    fun issue101_r5_sheetShowsTitleAndHint() {
        rule.setSheet(editorState(genres = listOf("Noir")))

        rule.onNodeWithTag(chipEdit.TITLE).assertTextEquals(
            detailText(R.string.book_detail_chipedit_title, detailText(R.string.book_detail_chips_genres), TITLE_BOOK),
        )
        rule.onNodeWithText(detailText(R.string.book_detail_chipedit_hint)).assertExists()
    }

    // TC-79
    @Test
    fun issue101_r5_sheetShowsTitleAndHintForTags() {
        rule.setSheet(editorState(tags = listOf("Cozy")), kind = MetadataListKind.Tags)

        rule.onNodeWithTag(chipEdit.TITLE).assertTextEquals(
            detailText(R.string.book_detail_chipedit_title, detailText(R.string.book_detail_chips_tags), TITLE_BOOK),
        )
        rule.onNodeWithText(detailText(R.string.book_detail_chipedit_hint)).assertExists()
    }

    // TC-80
    @Test
    fun issue101_ac3_currentChipsAreRemoveButtons() {
        val values = listOf("Noir", "Epic", "Dystopia")
        rule.setSheet(editorState(genres = values))

        values.forEach { value ->
            rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_remove, value))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
        val sheet = rule.onNodeWithTag(chipEdit.SHEET).bounds()
        values.forEach { value ->
            val chip = rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_remove, value)).bounds()
            assertTrue("$value lies outside the sheet", chip.left >= sheet.left - 1 && chip.right <= sheet.right + 1)
        }
    }

    // TC-81
    @Test
    fun issue101_r5_emptyListShowsPlaceholder() {
        rule.setSheet(editorState(genres = emptyList()))

        rule.onNodeWithTag(chipEdit.EMPTY).assertTextEquals(detailText(R.string.book_detail_chipedit_empty))
        rule.onAllNodesWithTag(chipEdit.CHIP).assertCountEquals(0)
    }

    // TC-82
    @Test
    fun issue101_r5_fieldLabelMatchesKind() {
        rule.setSheet(editorState(genres = listOf("Noir")))

        rule.onNodeWithText(detailText(R.string.book_detail_chipedit_field_genres), useUnmergedTree = true).assertExists()
    }

    // TC-82
    @Test
    fun issue101_r5_fieldLabelMatchesKindForTags() {
        rule.setSheet(editorState(tags = listOf("Cozy")), kind = MetadataListKind.Tags)

        rule.onNodeWithText(detailText(R.string.book_detail_chipedit_field_tags), useUnmergedTree = true).assertExists()
    }

    // TC-83
    @Test
    fun issue101_ac3_tappingAChipCallsRemove() {
        rule.setSheet(editorState(genres = listOf("Noir", "Epic")))

        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_remove, "Noir")).performClick()
        rule.waitForIdle()

        assertEquals(listOf("Noir"), removed)
    }

    // TC-84
    @Test
    fun issue101_ac4_showsAtMostFiveSuggestionsWithBookCounts() {
        val pool = pool(
            "Alpha" to 1, "Beta" to 4, "Gamma" to 9, "Delta" to 2,
            "Epsilon" to 7, "Zeta" to 3, "Eta" to 5, "Theta" to 6,
        )
        rule.setSheet(editorState(pools = mapOf(MetadataListKind.Genres to pool)))

        rule.onAllNodesWithTag(chipEdit.SUGGESTION).assertCountEquals(5)
        rule.onNode(hasTestTag(chipEdit.SUGGESTION) and hasText("Alpha")).assertTextEquals("Alpha", "1 book")
        rule.onNode(hasTestTag(chipEdit.SUGGESTION) and hasText("Beta")).assertTextEquals("Beta", "4 books")
        rule.onNode(hasTestTag(chipEdit.SUGGESTION) and hasText("Gamma")).assertTextEquals("Gamma", "9 books")
    }

    // TC-85
    @Test
    fun issue101_ac4_typingFiltersTheSuggestions() {
        val pool = pool("Noir" to 1, "Urban Fantasy" to 12, "Cozy" to 2, "Fantasy Epic" to 3, "Dark" to 4)
        rule.setSheet(editorState(pools = mapOf(MetadataListKind.Genres to pool)))

        rule.onNodeWithTag(chipEdit.FIELD).performTextReplacement("fan")
        rule.waitForIdle()

        rule.onAllNodesWithTag(chipEdit.SUGGESTION).assertCountEquals(2)
        rule.onAllNodes(hasTestTag(chipEdit.SUGGESTION) and hasText("Noir")).assertCountEquals(0)
        rule.onAllNodes(hasTestTag(chipEdit.SUGGESTION) and hasText("Cozy")).assertCountEquals(0)
    }

    // TC-86
    @Test
    fun issue101_ac4_pickingASuggestionAddsItsSpelling() {
        rule.setSheet(editorState(pools = mapOf(MetadataListKind.Genres to pool("Urban Fantasy" to 12))))

        rule.onNode(hasTestTag(chipEdit.SUGGESTION) and hasText("Urban Fantasy")).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf("Urban Fantasy"), added)
    }

    // TC-87
    @Test
    fun issue101_ac4_suggestionsLeaveOutCurrentChips() {
        rule.setSheet(
            editorState(genres = listOf("Cozy"), pools = mapOf(MetadataListKind.Genres to pool("cozy" to 3, "Noir" to 1))),
        )

        rule.onAllNodesWithTag(chipEdit.SUGGESTION).assertCountEquals(1)
        rule.onAllNodes(hasTestTag(chipEdit.SUGGESTION) and hasText("cozy", ignoreCase = true)).assertCountEquals(0)
    }

    // TC-88
    @Test
    fun issue101_ac4_addAsNewRowShowsOnlyForUnmatchedText() {
        val holder = rule.setSheet(
            editorState(genres = listOf("Cozy"), entry = "Grimdark", pools = mapOf(MetadataListKind.Genres to pool("Noir" to 1))),
        )

        rule.onNodeWithTag(chipEdit.CREATE).assertTextEquals(detailText(R.string.book_detail_chipedit_create_genre, "Grimdark"))

        listOf("Noir", "cozy", "  ").forEach { text ->
            rule.runOnIdle { holder.value = holder.value.copy(entry = text) }
            rule.waitForIdle()
            rule.onAllNodesWithTag(chipEdit.CREATE).assertCountEquals(0)
        }
    }

    // TC-89
    @Test
    fun issue101_ac4_createRowAndDoneAddTheTrimmedText() {
        val holder = rule.setSheet(editorState(entry = "Grimdark"))

        rule.onNodeWithTag(chipEdit.CREATE).performClick()
        rule.waitForIdle()
        assertEquals(listOf("Grimdark"), added)

        rule.runOnIdle { holder.value = holder.value.copy(entry = "  Grimdark ") }
        rule.onNodeWithTag(chipEdit.FIELD).performImeAction()
        rule.waitForIdle()
        assertEquals(listOf("Grimdark", "Grimdark"), added)

        rule.runOnIdle { holder.value = holder.value.copy(entry = "   ") }
        rule.onNodeWithTag(chipEdit.FIELD).performImeAction()
        rule.waitForIdle()
        assertEquals(listOf("Grimdark", "Grimdark"), added)
    }

    // TC-90
    @Test
    fun issue101_r8_fieldTakesAtMost128Characters() {
        rule.setSheet(editorState())

        rule.onNodeWithTag(chipEdit.FIELD).performTextReplacement("a".repeat(130))
        rule.waitForIdle()

        assertTrue(typed.isNotEmpty())
        assertEquals(128, typed.last().length)
    }

    // TC-91
    @Test
    fun issue101_r8_fullListDisablesTheField() {
        rule.setSheet(editorState(genres = List(64) { "genre $it" }))

        rule.onNodeWithTag(chipEdit.FIELD).assertIsNotEnabled()
        rule.onNodeWithTag(chipEdit.FULL).assertTextEquals(detailText(R.string.book_detail_chipedit_full))
    }

    // TC-92
    @Test
    fun issue101_ac2_offlineDisablesTheSheet() {
        val holder = rule.setSheet(
            editorState(genres = listOf("Noir"), isOnline = false, pools = mapOf(MetadataListKind.Genres to pool("Epic" to 1))),
        )

        rule.onNodeWithTag(chipEdit.FIELD).assertIsNotEnabled()
        rule.onNodeWithTag(chipEdit.SUGGESTION).assertIsNotEnabled()
        rule.onNodeWithTag(chipEdit.CHIP).assertIsNotEnabled()
        rule.onNodeWithTag(chipEdit.OFFLINE).assertTextEquals(detailText(R.string.book_detail_chipedit_offline))
        rule.onNodeWithTag(chipEdit.CHIP).performClick()
        rule.onNodeWithTag(chipEdit.SUGGESTION).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), removed)
        assertEquals(emptyList<String>(), added)

        rule.runOnIdle { holder.value = holder.value.copy(isOnline = true) }
        rule.onNodeWithTag(chipEdit.CHIP).assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(listOf("Noir"), removed)
    }

    // TC-94
    @Test
    fun issue101_ac4_poolLoadingStillAllowsAddAsNew() {
        rule.setSheet(editorState(entry = "Grimdark", pools = mapOf(MetadataListKind.Genres to PoolState.Loading)))

        rule.onAllNodesWithTag(chipEdit.SUGGESTION).assertCountEquals(0)
        rule.onNodeWithTag(chipEdit.CREATE).assertExists()
    }

    // TC-94
    @Test
    fun issue101_ac4_poolFailedStillAllowsAddAsNew() {
        rule.setSheet(editorState(entry = "Grimdark", pools = mapOf(MetadataListKind.Genres to PoolState.Failed)))

        rule.onAllNodesWithTag(chipEdit.SUGGESTION).assertCountEquals(0)
        rule.onNodeWithTag(chipEdit.CREATE).assertExists()
    }

    // TC-93
    @Test
    fun issue101_r5_backDismissesTheSheet() {
        val editor = FakeChipEditor(editorState(genres = listOf("Noir"), openKind = MetadataListKind.Genres))
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ChipEditOverlay(editor, testSectionContext(), autoFocus = false)
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(chipEdit.SHEET).assertExists()
        rule.pressBackKey()

        assertEquals(1, editor.calls.count { it == "close" })
    }

    // TC-93
    @Test
    fun issue101_r5_noSheetWhenNothingIsOpen() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ChipEditOverlay(FakeChipEditor(editorState()), testSectionContext(), autoFocus = false)
            }
        }
        rule.waitForIdle()

        rule.onAllNodesWithTag(chipEdit.SHEET).assertCountEquals(0)
    }
}
