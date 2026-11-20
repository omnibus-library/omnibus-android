package com.omnibus.omnibus.feature.reader.note

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.ui.ReaderController
import com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus
import com.omnibus.omnibus.feature.reader.ui.ReaderScreenContent
import com.omnibus.omnibus.feature.reader.ui.ReaderScreenTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        onDelete: () -> Unit = {},
        onDeleteConfirmed: () -> Unit = {},
        onDeleteCancelled: () -> Unit = {},
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
                    onDelete = onDelete,
                    onDeleteConfirmed = onDeleteConfirmed,
                    onDeleteCancelled = onDeleteCancelled,
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

    /** Sets the whole reader screen, in inspection mode so no page loads, with an open note screen. */
    private fun showInReader(
        onNoteCloseRequested: () -> Unit = {},
        onClose: () -> Unit = {},
        bottomDock: @Composable () -> Unit = {},
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = ReaderAssetLoaderFactory(context, Dispatchers.Unconfined, BookFileSource { BookFileResult.NotFound })
        val uiState = ReaderUIState(
            book = ReaderBookState.Ready(title = "Test Book"),
            loadStatus = ReaderLoadStatus.Ready,
            noteComposer = NoteComposerState(cfi, "Quote"),
        )
        rule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                OmnibusTheme {
                    ReaderScreenContent(
                        uiState = uiState,
                        bookUuid = "book-1",
                        controller = ReaderController(),
                        assetLoaderFactory = loader,
                        commands = emptyFlow(),
                        onPageEvent = {},
                        onSheetDismissed = {},
                        onTocEntryChosen = {},
                        onMenuOpened = {},
                        onMenuDismissed = {},
                        onMenuRowChosen = {},
                        onRetryBookRequested = {},
                        onReaderClosed = {},
                        onReaderStopped = {},
                        onSyncOfferAccepted = {},
                        onSyncOfferDismissed = {},
                        onClose = onClose,
                        onNoteCloseRequested = onNoteCloseRequested,
                        bottomDock = bottomDock,
                    )
                }
            }
        }
    }

    // TC-24, R15: back closes the note screen first, and the reader stays open
    @Test
    fun issue331_tc24_backClosesTheNoteScreenFirst() {
        val noteClosed = AtomicInteger(0)
        val closed = AtomicInteger(0)
        showInReader(
            onNoteCloseRequested = { noteClosed.incrementAndGet() },
            onClose = { closed.incrementAndGet() },
        )
        rule.waitForIdle()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertEquals(1, noteClosed.get())
        assertEquals(0, closed.get())
    }

    // TC-25, R4 and R14: the note screen covers the page and the dock, and takes the dock's touches
    @Test
    fun issue331_tc25_theNoteScreenCoversTheReaderAndTheDock() {
        val docked = AtomicInteger(0)
        showInReader(
            bottomDock = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.1f)
                        .clickable { docked.incrementAndGet() }
                        .testTag("test_dock"),
                )
            },
        )
        rule.waitForIdle()
        val reader = rule.onNodeWithTag(ReaderScreenTags.ROOT).fetchSemanticsNode().boundsInRoot
        val note = rule.onNodeWithTag(ReaderNoteScreenTags.ROOT).fetchSemanticsNode().boundsInRoot
        val dock = rule.onNodeWithTag("test_dock").fetchSemanticsNode().boundsInRoot

        assertEquals(reader.top, note.top, 1f)
        assertEquals(reader.bottom, note.bottom, 1f)
        assertTrue("the note screen covers the dock's top edge", note.top <= dock.top + 1f)
        assertTrue("the note screen covers the dock's bottom edge", note.bottom >= dock.bottom - 1f)

        rule.onNodeWithTag(ReaderNoteScreenTags.ROOT).performTouchInput {
            click(Offset(dock.center.x - note.left, dock.center.y - note.top))
        }

        assertEquals(0, docked.get())
    }

    // #332 TC-22: edit mode shows the edit title, the note in the field, and the trash button left of Save
    @Test
    fun issue332_tc22_editModeShowsTitleNoteAndTrash() {
        val note = "A note to keep"
        val state = mutableStateOf(
            NoteComposerState(cfiRange = cfi, quote = "A passage", text = note, existing = note),
        )
        show(state = { state.value })

        rule.onNodeWithTag(ReaderNoteScreenTags.TITLE).assertTextEquals("Edit note")
        rule.onNodeWithTag(ReaderNoteScreenTags.FIELD).assertTextEquals(note)
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).assertIsDisplayed()

        val trash = rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).fetchSemanticsNode().boundsInRoot
        val save = rule.onNodeWithTag(ReaderNoteScreenTags.SAVE).fetchSemanticsNode().boundsInRoot
        assertTrue("the trash button sits left of Save", trash.right <= save.left + 1f)
    }

    // #332 TC-22: a new note has no trash button and keeps the new title
    @Test
    fun issue332_tc22_newModeHasNoTrash() {
        show(state = { NoteComposerState(cfiRange = cfi, quote = null) })

        rule.onNodeWithTag(ReaderNoteScreenTags.TITLE).assertTextEquals("New note")
        rule.onAllNodesWithTag(ReaderNoteScreenTags.DELETE).assertCountEquals(0)
    }

    // #332 TC-23: the trash button is named "Delete note", is off while a write is in flight, and reads "Deleting"
    @Test
    fun issue332_tc23_trashFollowsTheState() {
        val note = "A note to keep"
        val state = mutableStateOf(NoteComposerState(cfiRange = cfi, quote = null, text = note, existing = note))
        show(state = { state.value })

        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).assertIsEnabled()
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).assertContentDescriptionEquals("Delete note")

        rule.runOnIdle { state.value = state.value.copy(saving = true) }
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).assertIsNotEnabled()

        rule.runOnIdle { state.value = state.value.copy(saving = false, deleting = true) }
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).assertIsNotEnabled()
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).assertContentDescriptionEquals("Delete note")
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Deleting"))
    }

    // #332 TC-24: the trash button asks, and the dialog's Delete and Cancel each reach their callback once
    @Test
    fun issue332_tc24_theDeleteDialogRoutesItsButtons() {
        val note = "A note to keep"
        val state = mutableStateOf(NoteComposerState(cfiRange = cfi, quote = null, text = note, existing = note))
        val opened = AtomicInteger(0)
        val cancelled = AtomicInteger(0)
        val confirmed = AtomicInteger(0)
        show(
            state = { state.value },
            onDelete = { opened.incrementAndGet() },
            onDeleteConfirmed = { confirmed.incrementAndGet() },
            onDeleteCancelled = { cancelled.incrementAndGet() },
        )

        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE).performClick()
        assertEquals(1, opened.get())

        rule.runOnIdle { state.value = state.value.copy(confirmingDelete = true) }
        rule.onNodeWithText("Delete this note?").assertIsDisplayed()
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE_CANCEL).performClick()
        assertEquals(1, cancelled.get())

        rule.runOnIdle { state.value = state.value.copy(confirmingDelete = false) }
        rule.runOnIdle { state.value = state.value.copy(confirmingDelete = true) }
        rule.onNodeWithTag(ReaderNoteScreenTags.DELETE_CONFIRM).performClick()
        assertEquals(1, confirmed.get())
        assertEquals(1, opened.get())
        assertEquals(1, cancelled.get())
    }

    // #332 TC-25: a failed delete shows its own error, a failed save shows the save error, and only one shows
    @Test
    fun issue332_tc25_errorTextFollowsTheFailedWrite() {
        val note = "A note to keep"
        val state = mutableStateOf(
            NoteComposerState(cfiRange = cfi, quote = null, text = note, existing = note, deleteFailed = true),
        )
        show(state = { state.value })

        rule.onAllNodesWithText("Couldn't delete note").assertCountEquals(1)
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(deleteFailed = false, failed = true) }
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(1)
        rule.onAllNodesWithText("Couldn't delete note").assertCountEquals(0)

        rule.runOnIdle { state.value = state.value.copy(deleteFailed = true) }
        rule.onAllNodesWithText("Couldn't delete note").assertCountEquals(1)
        rule.onAllNodesWithText("Couldn't save note").assertCountEquals(0)
    }
}
