package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The review sheet's body, rendered directly with a state and recording callbacks: the header and Save, the review field,
 * the private notes, the spoiler switch, the typing layout and the discard dialog.
 */
class ReviewSheetContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val book = testDetailBook()

    /** A sheet state with the defaults of a fresh review, to change one part of at a time. */
    private fun sheet(
        shown: ReviewFields = ReviewFields.Empty,
        saveEnabled: Boolean = false,
        confirmingDiscard: Boolean = false,
    ) = ReviewSheetUIState(
        open = true,
        shown = shown,
        saveEnabled = saveEnabled,
        confirmingDiscard = confirmingDiscard,
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
    fun tc69_saveFollowsItsEnabledState() {
        var saves = 0
        val state = mutableStateOf(sheet(saveEnabled = false))
        show(state, ReviewSheetActions(onSave = { saves++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).performClick()
        assertEquals(0, saves)

        state.value = sheet(saveEnabled = true)
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.SAVE).performClick()
        assertEquals(1, saves)
    }

    @Test
    fun tc70_CancelCallsBackOnce() {
        var cancels = 0
        show(mutableStateOf(sheet()), ReviewSheetActions(onCancel = { cancels++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.CANCEL).performClick()

        assertEquals(1, cancels)
    }

    @Test
    fun tc72_typingInTheReviewFieldReportsTheText() {
        val texts = mutableListOf<String>()
        show(mutableStateOf(sheet()), ReviewSheetActions(onTextChange = { texts += it }))

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performTextInput("Hi")

        assertEquals("Hi", texts.last())
    }

    @Test
    fun tc73_tappingPrivateNotesExpandsThem() {
        var expands = 0
        show(mutableStateOf(sheet()), ReviewSheetActions(onExpandNotes = { expands++ }))

        val row = rule.onNodeWithTag(ReviewSheetTestTags.NOTES_ROW)
        row.performScrollTo()
        row.performClick()

        assertEquals(1, expands)
    }

    @Test
    fun tc74_tappingTheSpoilerSwitchReportsTheNewValue() {
        val spoilers = mutableListOf<Boolean>()
        show(mutableStateOf(sheet(shown = ReviewFields(null, null, null, false))), ReviewSheetActions(onSpoilerChange = { spoilers += it }))

        val spoiler = rule.onNodeWithTag(ReviewSheetTestTags.SPOILER)
        spoiler.performScrollTo()
        spoiler.performClick()

        assertEquals(listOf(true), spoilers)
    }

    @Test
    fun tc78_focusingTheReviewFieldShowsTheTypingLayout() {
        show(mutableStateOf(sheet(shown = ReviewFields(4.5f, null, null, false))))

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(ReviewSheetTestTags.STARS).assertDoesNotExist()
        rule.onNodeWithTag(ReviewSheetTestTags.COMPACT_STARS).assertExists()
    }

    @Test
    fun tc79_inTypingModeHighlightsCallsBackAndDoneReturnsToTheFullLayout() {
        var opens = 0
        val state = mutableStateOf(sheet(shown = ReviewFields(4.5f, null, null, false)))
        show(state, ReviewSheetActions(onOpenPicker = { opens++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.FIELD).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.ACCESSORY_HIGHLIGHTS).performClick()
        assertEquals(1, opens)

        rule.onNodeWithTag(ReviewSheetTestTags.DONE).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(ReviewSheetTestTags.STARS).assertExists()
    }

    @Test
    fun tc80_FromHighlightsCallsBackInTheFullLayout() {
        var opens = 0
        show(mutableStateOf(sheet()), ReviewSheetActions(onOpenPicker = { opens++ }))

        rule.onNodeWithTag(ReviewSheetTestTags.FROM_HIGHLIGHTS).performScrollTo()
        rule.onNodeWithTag(ReviewSheetTestTags.FROM_HIGHLIGHTS).performClick()

        assertEquals(1, opens)
    }

    @Test
    fun tc81_theDiscardDialogIsShownOnlyWhileConfirmingAndItsButtonsCallBack() {
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
    fun tc83_theTitleIsAHeading() {
        show(mutableStateOf(sheet()))

        rule.onNodeWithTag(ReviewSheetTestTags.TITLE).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }
}
