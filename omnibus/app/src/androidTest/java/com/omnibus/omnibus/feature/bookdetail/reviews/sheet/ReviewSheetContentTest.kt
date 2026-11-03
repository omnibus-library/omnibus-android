package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The review sheet's body, rendered directly with a state and recording callbacks: the header and Save, the fields, the
 * private notes, the spoiler switch, the conflict note, the failure banner, the typing layout and the discard dialog.
 * Sizes are minimum touch targets, never fixed.
 */
class ReviewSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()

    /** A sheet state with the defaults of a fresh review, to change one part of at a time. */
    private fun sheet(
        title: ReviewSheetTitle = ReviewSheetTitle.Write,
        shown: ReviewFields = ReviewFields.Empty,
        saveEnabled: Boolean = false,
        saving: Boolean = false,
        conflicts: List<ReviewField> = emptyList(),
        notesExpanded: Boolean = false,
        confirmingDiscard: Boolean = false,
        failure: ReviewSheetFailure? = null,
    ) = ReviewSheetUIState(
        open = true,
        title = title,
        shown = shown,
        saveEnabled = saveEnabled,
        saving = saving,
        conflicts = conflicts,
        notesExpanded = notesExpanded,
        confirmingDiscard = confirmingDiscard,
        failure = failure,
    )

    /** Shows the body for the state in [state], so a test can change it between steps. */
    private fun show(state: MutableState<ReviewSheetUIState>, actions: ReviewSheetActions = ReviewSheetActions()) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ReviewSheetContent(state.value, book, actions, reducedMotion = true)
                }
            }
        }
    }

    @Test
    fun `TC-68 the header has Cancel at the start, the title as a heading, and Save at the end`() {
        val state = mutableStateOf(sheet(title = ReviewSheetTitle.Write))
        show(state)

        rule.onNodeWithTag(ReviewSheetTestTags.TITLE).assertTextEquals("Your review")
        rule.onNodeWithTag(ReviewSheetTestTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(ReviewSheetTestTags.CANCEL).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).assertExists()
        val cancelLeft = rule.onNodeWithTag(ReviewSheetTestTags.CANCEL).fetchSemanticsNode().boundsInRoot.left
        val titleLeft = rule.onNodeWithTag(ReviewSheetTestTags.TITLE).fetchSemanticsNode().boundsInRoot.left
        val saveLeft = rule.onNodeWithTag(ReviewSheetTestTags.SAVE).fetchSemanticsNode().boundsInRoot.left
        assertTrue(cancelLeft < titleLeft)
        assertTrue(titleLeft < saveLeft)

        state.value = sheet(title = ReviewSheetTitle.Edit)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.TITLE).assertTextEquals("Edit review")
    }

    @Test
    fun `TC-69 Save is disabled, then enabled and clickable, then disabled while saving`() {
        var saves = 0
        val state = mutableStateOf(sheet(saveEnabled = false))
        show(state, ReviewSheetActions(onSave = { saves++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).assertIsNotEnabled()
        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).performClick()
        assertEquals(0, saves)

        state.value = sheet(saveEnabled = true)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).assertIsEnabled()
        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).performClick()
        assertEquals(1, saves)

        state.value = sheet(saveEnabled = false, saving = true)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).assertIsNotEnabled()
    }

    @Test
    fun `TC-70 Cancel calls back once`() {
        var cancels = 0
        show(mutableStateOf(sheet()), ReviewSheetActions(onCancel = { cancels++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.CANCEL).performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun `TC-71 the review field shows its text, and an empty one shows the placeholder and the audience`() {
        val state = mutableStateOf(sheet(shown = ReviewFields(4.5f, "Better than Scarlet.", null, false)))
        show(state)

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).assertTextEquals("Better than Scarlet.")

        state.value = sheet(shown = ReviewFields.Empty)
        rule.waitForIdle()
        rule.onNodeWithText("What did you make of it?", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Review").assertExists()
        rule.onNodeWithText("Your household can read this").assertExists()
    }

    @Test
    fun `TC-72 typing in the review field reports the text`() {
        val texts = mutableListOf<String>()
        show(mutableStateOf(sheet()), ReviewSheetActions(onTextChange = { texts += it }))

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performTextInput("Hi")

        assertEquals("Hi", texts.last())
    }

    @Test
    fun `TC-73 private notes are one button when collapsed, and show a labelled field when expanded`() {
        var expands = 0
        val state = mutableStateOf(sheet(notesExpanded = false))
        show(state, ReviewSheetActions(onExpandNotes = { expands++ }))

        val row = rule.onNodeWithTag(ReviewSheetTestTags.NOTES_ROW)
        row.performScrollTo()
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        row.assertTextContains("Private notes")
        row.assertTextContains("Only you can see these")
        row.performClick()
        assertEquals(1, expands)

        state.value = sheet(notesExpanded = true, shown = ReviewFields(null, null, "My private line", false))
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.NOTES_FIELD).assertTextEquals("My private line")

        state.value = sheet(notesExpanded = true, shown = ReviewFields.Empty)
        rule.waitForIdle()
        rule.onNodeWithText("Notes to yourself", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `TC-74 the spoiler switch is one toggle with its label, and a tap reports the new value`() {
        val spoilers = mutableListOf<Boolean>()
        val state = mutableStateOf(sheet(shown = ReviewFields(null, null, null, false)))
        show(state, ReviewSheetActions(onSpoilerChange = { spoilers += it }))

        val spoiler = rule.onNodeWithTag(ReviewSheetTestTags.SPOILER)
        spoiler.performScrollTo()
        spoiler.assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
        spoiler.assertTextContains("This review gives away the story.")
        spoiler.performClick()
        assertEquals(listOf(true), spoilers)

        state.value = sheet(shown = ReviewFields(null, null, null, true))
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.SPOILER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun `TC-75 the conflict note names the one field, or says several, and is absent without a conflict`() {
        val state = mutableStateOf(sheet(conflicts = emptyList()))
        show(state)
        rule.onNodeWithTag(ReviewSheetTestTags.CONFLICT).assertDoesNotExist()

        val cases = mapOf(
            listOf(ReviewField.Rating) to "Your rating changed elsewhere while this was open. Save keeps yours.",
            listOf(ReviewField.Text) to "Your review text changed elsewhere while this was open. Save keeps yours.",
            listOf(ReviewField.Notes) to "Your private notes changed elsewhere while this was open. Save keeps yours.",
            listOf(ReviewField.Spoiler) to "Your spoiler setting changed elsewhere while this was open. Save keeps yours.",
            listOf(ReviewField.Rating, ReviewField.Text) to
                "Parts of your review changed elsewhere while this was open. Save keeps yours.",
        )
        cases.forEach { (conflicts, copy) ->
            state.value = sheet(conflicts = conflicts)
            rule.waitForIdle()
            rule.onNodeWithTag(ReviewSheetTestTags.CONFLICT).assertTextEquals(copy)
        }

        state.value = sheet(conflicts = emptyList())
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.CONFLICT).assertDoesNotExist()
    }

    @Test
    fun `TC-76 the failure banner shows the offline copy when offline, and nothing without a failure`() {
        val state = mutableStateOf(sheet(failure = ReviewSheetFailure(offline = false)))
        show(state)
        rule.onNodeWithTag(ReviewSheetTestTags.FAILURE).assertTextEquals("Your review wasn't saved.")

        state.value = sheet(failure = ReviewSheetFailure(offline = true))
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.FAILURE).assertTextEquals("No connection. Your review wasn't saved.")

        state.value = sheet(failure = null)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.FAILURE).assertDoesNotExist()
    }

    @Test
    fun `TC-77 the readout gives the rating and its word, or the prompt when unrated`() {
        val state = mutableStateOf(sheet(shown = ReviewFields(4.5f, null, null, false)))
        show(state)
        rule.onNodeWithTag(ReviewSheetTestTags.READOUT).assertTextEquals("4.5 Loved it")

        state.value = sheet(shown = ReviewFields.Empty)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.READOUT).assertTextEquals("Tap or drag. Half stars count.")
    }

    @Test
    fun `TC-78 focusing the review field shows the typing layout, with compact stars and the accessory row`() {
        val state = mutableStateOf(sheet(shown = ReviewFields(4.5f, null, null, false)))
        show(state)

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(ReviewSheetTestTags.COVER).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.BOOK_TITLE).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.STARS).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.READOUT).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.NOTES_ROW).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.SPOILER).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.FROM_HIGHLIGHTS).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.COMPACT_STARS).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.COMPACT_READOUT).assertTextEquals("4.5 · Loved it")
        rule.onNodeWithTag(ReviewSheetTestTags.ACCESSORY_HIGHLIGHTS).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.DONE).assertExists()

        state.value = sheet(shown = ReviewFields.Empty)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.COMPACT_READOUT).assertTextEquals("Not rated yet")
    }

    @Test
    fun `TC-79 in typing mode Highlights calls back, and Done returns to the full layout`() {
        var opens = 0
        val state = mutableStateOf(sheet(shown = ReviewFields(4.5f, null, null, false)))
        show(state, ReviewSheetActions(onOpenPicker = { opens++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.ACCESSORY_HIGHLIGHTS).performClick()
        assertEquals(1, opens)

        rule.onNodeWithTag(ReviewSheetTestTags.DONE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.COVER).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.STARS).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.NOTES_ROW).assertExists()
        rule.onNodeWithTag(ReviewSheetTestTags.DONE).assertDoesNotExist()
    }

    @Test
    fun `TC-80 From highlights calls back in the full layout`() {
        var opens = 0
        show(mutableStateOf(sheet()), ReviewSheetActions(onOpenPicker = { opens++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.FROM_HIGHLIGHTS).performScrollTo()
        rule.onNodeWithTag(ReviewSheetTestTags.FROM_HIGHLIGHTS).performClick()

        assertEquals(1, opens)
    }

    @Test
    fun `TC-81 the discard dialog is shown only while confirming, and its buttons call back`() {
        var discards = 0
        var keeps = 0
        val state = mutableStateOf(sheet(confirmingDiscard = false))
        show(state, ReviewSheetActions(onDiscard = { discards++ }, onKeepEditing = { keeps++ }))
        rule.onNodeWithTag(ReviewSheetTestTags.DISCARD).assertDoesNotExist()

        state.value = sheet(confirmingDiscard = true)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.DISCARD).performClick()
        assertEquals(1, discards)

        state.value = sheet(confirmingDiscard = true)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.KEEP_EDITING).performClick()
        assertEquals(1, keeps)
    }

    @Test
    fun `TC-82 the targets are at least 48dp in both layouts`() {
        val state = mutableStateOf(sheet(shown = ReviewFields(4.5f, null, null, false)))
        show(state)

        listOf(
            ReviewSheetTestTags.CANCEL,
            ReviewSheetTestTags.SAVE,
            ReviewSheetTestTags.NOTES_ROW,
            ReviewSheetTestTags.SPOILER,
            ReviewSheetTestTags.FROM_HIGHLIGHTS,
        ).forEach { tag ->
            rule.onNodeWithTag(tag).performScrollTo()
            rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
            rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp)
        }

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.waitForIdle()
        listOf(ReviewSheetTestTags.ACCESSORY_HIGHLIGHTS, ReviewSheetTestTags.DONE).forEach { tag ->
            rule.onNodeWithTag(tag).assertHeightIsAtLeast(48.dp)
            rule.onNodeWithTag(tag).assertWidthIsAtLeast(48.dp)
        }
    }

    @Test
    fun `TC-83 the title is a heading, and the star row exposes no child nodes`() {
        show(mutableStateOf(sheet(shown = ReviewFields(3f, null, null, false))))

        rule.onNodeWithTag(ReviewSheetTestTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(ReviewSheetTestTags.STARS).onChildren().assertCountEquals(0)
    }

    @Test
    fun `TC-105 the roomy body scrolls, and the typing body does not`() {
        show(mutableStateOf(sheet()))

        rule.onNodeWithTag(ReviewSheetTestTags.BODY).assert(hasScrollAction())
        rule.onNodeWithTag(ReviewSheetTestTags.NOTES_ROW).performScrollTo()

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.BODY)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.ScrollBy))
    }
}
