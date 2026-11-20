package com.omnibus.omnibus.feature.reader.note

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReaderNoteScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val cfi = "epubcfi(/6/2!/4/2,/1:0,/1:5)"

    /** Sets the screen with [state] read on every composition, so a change to the state shows up without setContent. */
    private fun show(
        state: () -> NoteComposerState,
        onTextChanged: (String) -> Unit = {},
        onSave: () -> Unit = {},
        onClose: () -> Unit = {},
        onDiscardConfirmed: () -> Unit = {},
        onDiscardCancelled: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme {
                ReaderNoteScreen(
                    state = state(),
                    onTextChanged = onTextChanged,
                    onSave = onSave,
                    onClose = onClose,
                    onDiscardConfirmed = onDiscardConfirmed,
                    onDiscardCancelled = onDiscardCancelled,
                )
            }
        }
    }

    // TC-20: the field is focused on open, the controls are named, and a blank quote shows none
    @Test
    fun issue331_tc20_onOpenTheFieldIsFocusedAndControlsAreNamed() {
        val state = mutableStateOf(NoteComposerState(cfiRange = cfi, quote = "A passage"))
        show(state = { state.value })

        rule.onNodeWithTag(ReaderNoteScreenTags.FIELD).assertIsFocused()
        rule.onNodeWithTag(ReaderNoteScreenTags.FIELD).assertContentDescriptionEquals("Note")
        rule.onAllNodesWithContentDescription("Close").assertCountEquals(1)
        rule.onNodeWithTag(ReaderNoteScreenTags.TITLE).assertTextEquals("New note")
        rule.onNodeWithTag(ReaderNoteScreenTags.TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        rule.onNodeWithTag(ReaderNoteScreenTags.QUOTE).assertTextEquals("A passage")
        rule.onAllNodesWithText("Delete").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(quote = "   ") }

        rule.onAllNodesWithTag(ReaderNoteScreenTags.QUOTE).assertCountEquals(0)
    }

    // TC-21: Save follows whether there is text and a save is in flight, and the error shows only after a failure
    @Test
    fun issue331_tc21_saveStateAndErrorFollowTheState() {
        val state = mutableStateOf(NoteComposerState(cfiRange = cfi, quote = null))
        show(state = { state.value })

        rule.onNodeWithTag(ReaderNoteScreenTags.SAVE).assertIsNotEnabled()
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(text = "hi") }

        rule.onNodeWithTag(ReaderNoteScreenTags.SAVE).assertIsEnabled()
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(saving = true) }

        rule.onNodeWithTag(ReaderNoteScreenTags.SAVE).assertIsNotEnabled()
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(saving = false, failed = true) }

        rule.onNodeWithTag(ReaderNoteScreenTags.SAVE).assertIsEnabled()
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(1)
    }

    // TC-22: typing, Save and close each reach their callback once
    @Test
    fun issue331_tc22_callbacksFireOnTypingSaveAndClose() {
        val text = mutableStateOf("")
        val typed = mutableListOf<String>()
        val saves = AtomicInteger(0)
        val closes = AtomicInteger(0)
        show(
            state = { NoteComposerState(cfiRange = cfi, quote = null, text = text.value) },
            onTextChanged = {
                typed += it
                text.value = it
            },
            onSave = { saves.incrementAndGet() },
            onClose = { closes.incrementAndGet() },
        )

        rule.onNodeWithTag(ReaderNoteScreenTags.FIELD).performTextInput("hi")
        rule.onNodeWithTag(ReaderNoteScreenTags.SAVE).performClick()
        rule.onNodeWithTag(ReaderNoteScreenTags.CLOSE).performClick()

        assertEquals("hi", typed.last())
        assertEquals(1, saves.get())
        assertEquals(1, closes.get())
    }

    // TC-23: the discard dialog's two buttons reach their callbacks
    @Test
    fun issue331_tc23_theDiscardDialogRoutesItsButtons() {
        val state = mutableStateOf(
            NoteComposerState(cfiRange = cfi, quote = null, text = "hi", confirmingDiscard = true),
        )
        val cancelled = AtomicInteger(0)
        val confirmed = AtomicInteger(0)
        show(
            state = { state.value },
            onDiscardConfirmed = { confirmed.incrementAndGet() },
            onDiscardCancelled = { cancelled.incrementAndGet() },
        )

        rule.onNodeWithText("Discard note?").assertIsDisplayed()
        rule.onNodeWithTag(ReaderNoteScreenTags.KEEP_WRITING).performClick()
        rule.onNodeWithTag(ReaderNoteScreenTags.DISCARD).performClick()

        assertEquals(1, cancelled.get())
        assertEquals(1, confirmed.get())
    }
}
