package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.data.metadata.VocabularyEntry
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

/** The sheet's content: removing, adding, the length limit, offline and dismissal. ChipEditSheetScreenshotTest covers how it looks. */
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

    // TC-80
    @Test
    fun issue101_ac3_currentChipsAreRemoveButtons() {
        val values = listOf("Noir", "Epic", "Dystopia")
        rule.setSheet(editorState(genres = values))

        values.forEach { value ->
            rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_remove, value))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        }
    }

    // TC-83
    @Test
    fun issue101_ac3_tappingAChipCallsRemove() {
        rule.setSheet(editorState(genres = listOf("Noir", "Epic")))

        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_remove, "Noir")).performClick()
        rule.waitForIdle()

        assertEquals(listOf("Noir"), removed)
    }

    // TC-86
    @Test
    fun issue101_ac4_pickingASuggestionAddsItsSpelling() {
        rule.setSheet(editorState(pools = mapOf(MetadataListKind.Genres to pool("Urban Fantasy" to 12))))

        rule.onNode(hasTestTag(chipEdit.SUGGESTION) and hasText("Urban Fantasy")).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf("Urban Fantasy"), added)
    }

    // TC-89
    @Test
    fun issue101_ac4_committingAddsTheTrimmedText() {
        val holder = rule.setSheet(editorState(entry = "Grimdark"))

        rule.onNodeWithTag(chipEdit.CREATE).performClick()
        rule.waitForIdle()
        assertEquals(listOf("Grimdark"), added)

        rule.runOnIdle { holder.value = holder.value.copy(entry = "  Grimdark ") }
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

    // TC-92
    @Test
    fun issue101_ac2_offlineDisablesTheSheet() {
        rule.setSheet(
            editorState(genres = listOf("Noir"), isOnline = false, pools = mapOf(MetadataListKind.Genres to pool("Epic" to 1))),
        )

        rule.onNodeWithTag(chipEdit.FIELD).assertIsNotEnabled()
        rule.onNodeWithTag(chipEdit.CHIP).performClick()
        rule.onNodeWithTag(chipEdit.SUGGESTION).performClick()
        rule.waitForIdle()
        assertEquals(emptyList<String>(), removed)
        assertEquals(emptyList<String>(), added)
    }

    // TC-93
    @Test
    fun issue101_r5_backDismissesTheSheet() {
        val editor = TestChipEditor(editorState(genres = listOf("Noir"), openKind = MetadataListKind.Genres))
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
}
