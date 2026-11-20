package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightError
import com.omnibus.omnibus.data.highlights.HighlightNoteResult
import com.omnibus.omnibus.data.highlights.HighlightWriteResult
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.feature.reader.note.NoteComposerState
import com.omnibus.omnibus.feature.reader.ui.AnnotationHit
import com.omnibus.omnibus.feature.reader.ui.AnnotationTapData
import com.omnibus.omnibus.feature.reader.ui.ReaderEvent
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomPageRect
import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** Where the highlight a note is written on came from: a saved row, or one made this session. */
private enum class NoteOrigin { Saved, Session }

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelHighlightNoteTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("opening the note screen") {
        context("from the tap menu") {
            it("#331 TC-7: closes the highlight menu") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository())

                vm.noteFromTapOn(cfi)

                vm.uiState.value.highlightMenu.shouldBeNull()
            }

            it("#331 TC-7: opens the note screen on the highlight's range, quoting its text") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val vm = savedVm(row, highlights = repository())

                vm.noteFromTapOn(cfi)

                vm.uiState.value.noteComposer shouldBe NoteComposerState(cfiRange = cfi, quote = row.text)
            }
        }

        context("from a selection") {
            it("#331 TC-8: sends the selection as an amber highlight") {
                val cfi = randomCfi()
                val text = randomString()
                val highlights = repository(createGate = CompletableDeferred())
                val vm = sessionVm(highlights)

                vm.noteFromSelection(cfi, text)

                val draft = highlights.drafts.single()
                draft.color shouldBe HighlightColor.Amber
                draft.cfiRange shouldBe cfi
                draft.text shouldBe text
            }

            it("#331 TC-8: draws the amber highlight at once") {
                val cfi = randomCfi()
                val vm = sessionVm(repository(createGate = CompletableDeferred()))

                vm.noteFromSelection(cfi, randomString())

                vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe HighlightColor.Amber
            }

            it("#331 TC-8: clears the selection") {
                val vm = sessionVm(repository(createGate = CompletableDeferred()))

                vm.noteFromSelection(randomCfi(), randomString())

                vm.uiState.value.selection.shouldBeNull()
            }

            it("#331 TC-8: opens the note screen on the selection's range, quoting its text") {
                val cfi = randomCfi()
                val text = randomString()
                val vm = sessionVm(repository(createGate = CompletableDeferred()))

                vm.noteFromSelection(cfi, text)

                vm.uiState.value.noteComposer shouldBe NoteComposerState(cfiRange = cfi, quote = text)
            }
        }
    }

    describe("typing a note") {
        it("#331 TC-9: cuts the text at 4,096 code points without splitting a surrogate pair") {
            val cfi = randomCfi()
            val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository())
            vm.noteFromTapOn(cfi)
            val emoji = "😀"

            vm.onNoteTextChanged("a".repeat(4_095) + emoji + "more")

            vm.uiState.value.noteComposer.shouldNotBeNull().text shouldBe "a".repeat(4_095) + emoji
        }
    }

    describe("saving a note") {
        context("on a saved highlight") {
            it("#331 TC-10: sends the trimmed text for the saved id") {
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum())
                val highlights = repository(noteGate = CompletableDeferred())
                val vm = savedVm(row, highlights = highlights)
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged("  hi  ")

                vm.onNoteSaveRequested()

                highlights.notes shouldBe listOf(row.id to "hi")
            }

            it("#331 TC-10: marks the note screen as saving, without a failure") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository(noteGate = CompletableDeferred()))
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged(randomString())

                vm.onNoteSaveRequested()

                val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
                composer.saving shouldBe true
                composer.failed shouldBe false
            }

            it("#331 TC-10: ignores typing while the save is in flight") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository(noteGate = CompletableDeferred()))
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged("  hi  ")
                vm.onNoteSaveRequested()

                vm.onNoteTextChanged("more")

                vm.uiState.value.noteComposer.shouldNotBeNull().text shouldBe "  hi  "
            }
        }

        context("when the save succeeds") {
            it("#331 TC-11: closes the note screen for a saved or a session highlight") {
                checkAll(Exhaustive.enum<NoteOrigin>()) { origin ->
                    val vm = openedOn(origin, randomCfi(), randomString(), repository(savedId = randomLong()))

                    vm.onNoteSaveRequested()

                    vm.uiState.value.noteComposer.shouldBeNull()
                }
            }

            it("#331 TC-11: draws the note underline on the highlight for a saved or a session highlight") {
                checkAll(Exhaustive.enum<NoteOrigin>()) { origin ->
                    val cfi = randomCfi()
                    val vm = openedOn(origin, cfi, randomString(), repository(savedId = randomLong()))

                    vm.onNoteSaveRequested()

                    vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.hasNote shouldBe true
                }
            }
        }

        context("when the save fails") {
            it("#331 TC-12: keeps the typed text") {
                checkAll(Exhaustive.collection(allNoteErrors())) { error ->
                    val cfi = randomCfi()
                    val highlights = notesAnswering(HighlightNoteResult.Failed(error))
                    val vm = savedVm(savedAt(cfi, randomEnum()), highlights = highlights)
                    vm.noteFromTapOn(cfi)
                    vm.onNoteTextChanged("hi")

                    vm.onNoteSaveRequested()

                    vm.uiState.value.noteComposer.shouldNotBeNull().text shouldBe "hi"
                }
            }

            it("#331 TC-12: shows the error and stops saving") {
                checkAll(Exhaustive.collection(allNoteErrors())) { error ->
                    val cfi = randomCfi()
                    val highlights = notesAnswering(HighlightNoteResult.Failed(error))
                    val vm = savedVm(savedAt(cfi, randomEnum()), highlights = highlights)
                    vm.noteFromTapOn(cfi)
                    vm.onNoteTextChanged("hi")

                    vm.onNoteSaveRequested()

                    val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
                    composer.failed shouldBe true
                    composer.saving shouldBe false
                }
            }

            it("#331 TC-12: sends the note again when Save is tapped again") {
                checkAll(Exhaustive.collection(allNoteErrors())) { error ->
                    val cfi = randomCfi()
                    val row = savedAt(cfi, randomEnum())
                    val highlights = notesAnswering(HighlightNoteResult.Failed(error))
                    val vm = savedVm(row, highlights = highlights)
                    vm.noteFromTapOn(cfi)
                    vm.onNoteTextChanged("hi")
                    vm.onNoteSaveRequested()

                    vm.onNoteSaveRequested()

                    highlights.notes shouldBe List(2) { row.id to "hi" }
                }
            }
        }
    }

    describe("saving a note on a new selection") {
        it("#331 TC-13: sends no note before the amber save answers") {
            val createGate = CompletableDeferred<Unit>()
            val highlights = repository(createGate = createGate)
            val vm = sessionVm(highlights)
            vm.noteFromSelection(randomCfi(), randomString())
            vm.onNoteTextChanged("hi")

            vm.onNoteSaveRequested()

            highlights.notes.shouldBeEmpty()
        }

        it("#331 TC-13: sends the note for the saved amber highlight once it answers") {
            val createGate = CompletableDeferred<Unit>()
            val savedId = randomLong()
            val highlights = repository(createGate = createGate, savedId = savedId)
            val vm = sessionVm(highlights)
            vm.noteFromSelection(randomCfi(), randomString())
            vm.onNoteTextChanged("hi")
            vm.onNoteSaveRequested()

            createGate.complete(Unit)

            highlights.notes shouldBe listOf(savedId to "hi")
        }
    }

    describe("saving a note on a selection whose amber save fails") {
        context("on a new selection") {
            it("#331 TC-14: shows the save error on the note screen") {
                val vm = sessionVm(repository(creates = false))
                vm.noteFromSelection(randomCfi(), randomString())
                vm.onNoteTextChanged("hi")

                vm.onNoteSaveRequested()

                vm.uiState.value.noteComposer.shouldNotBeNull().failed shouldBe true
            }

            it("#331 TC-14: sends no note") {
                val highlights = repository(creates = false)
                val vm = sessionVm(highlights)
                vm.noteFromSelection(randomCfi(), randomString())
                vm.onNoteTextChanged("hi")

                vm.onNoteSaveRequested()

                highlights.notes.shouldBeEmpty()
            }

            it("#331 TC-14: raises the highlight save notice") {
                val vm = sessionVm(repository(creates = false))

                vm.noteFromSelection(randomCfi(), randomString())

                vm.uiState.value.notice shouldBe ReaderNotice.HighlightSaveFailed
            }
        }
    }

    describe("leaving the note screen without saving") {
        it("#331 TC-15: closes the note screen") {
            val vm = sessionVm(repository())
            vm.noteFromSelection(randomCfi(), randomString())
            vm.onNoteTextChanged("hi")
            vm.onNoteCloseRequested()

            vm.onNoteDiscardConfirmed()

            vm.uiState.value.noteComposer.shouldBeNull()
        }

        it("#331 TC-15: keeps the amber highlight on the page") {
            val cfi = randomCfi()
            val vm = sessionVm(repository())
            vm.noteFromSelection(cfi, randomString())
            vm.onNoteTextChanged("hi")
            vm.onNoteCloseRequested()

            vm.onNoteDiscardConfirmed()

            vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }.color shouldBe HighlightColor.Amber
        }

        it("#331 TC-15: sends no delete") {
            val highlights = repository()
            val vm = sessionVm(highlights)
            vm.noteFromSelection(randomCfi(), randomString())
            vm.onNoteTextChanged("hi")
            vm.onNoteCloseRequested()

            vm.onNoteDiscardConfirmed()

            highlights.deletes.shouldBeEmpty()
        }
    }

    describe("closing the note screen") {
        it("#331 TC-16: closes at once when the text is blank") {
            checkAll(Exhaustive.of("", "  ", "\n")) { blank ->
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository())
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged(blank)

                vm.onNoteCloseRequested()

                vm.uiState.value.noteComposer.shouldBeNull()
            }
        }

        context("when the text is not blank") {
            it("#331 TC-16: asks before discarding the text") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository())
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged(randomString())

                vm.onNoteCloseRequested()

                vm.uiState.value.noteComposer.shouldNotBeNull().confirmingDiscard shouldBe true
            }

            it("#331 TC-16: keeps the text while it asks") {
                val cfi = randomCfi()
                val text = randomString()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository())
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged(text)

                vm.onNoteCloseRequested()

                vm.uiState.value.noteComposer.shouldNotBeNull().text shouldBe text
            }
        }

        context("while a save is in flight") {
            it("#331 TC-16: does nothing") {
                val cfi = randomCfi()
                val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository(noteGate = CompletableDeferred()))
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged(randomString())
                vm.onNoteSaveRequested()
                val before = vm.uiState.value.noteComposer

                vm.onNoteCloseRequested()

                vm.uiState.value.noteComposer shouldBe before
            }
        }
    }

    describe("answering the discard dialog") {
        context("when the reader keeps writing") {
            it("#331 TC-17: closes the dialog") {
                val vm = openedWithTextAsking(randomString())

                vm.onNoteDiscardCancelled()

                vm.uiState.value.noteComposer.shouldNotBeNull().confirmingDiscard shouldBe false
            }

            it("#331 TC-17: keeps the text") {
                val text = randomString()
                val vm = openedWithTextAsking(text)

                vm.onNoteDiscardCancelled()

                vm.uiState.value.noteComposer.shouldNotBeNull().text shouldBe text
            }
        }

        context("when the reader keeps writing and then discards") {
            it("#331 TC-17: closes the note screen") {
                val vm = openedWithTextAsking(randomString())
                vm.onNoteDiscardCancelled()
                vm.onNoteCloseRequested()

                vm.onNoteDiscardConfirmed()

                vm.uiState.value.noteComposer.shouldBeNull()
            }
        }
    }

    describe("opening a note that already has text") {
        it("#332 TC-6: opens in edit mode filled with the note") {
            val cfi = randomCfi()
            val note = randomString()
            val row = savedAt(cfi, randomEnum()).copy(note = note)
            val vm = savedVm(row, highlights = repository())

            vm.noteFromTapOn(cfi)

            vm.uiState.value.noteComposer shouldBe
                NoteComposerState(cfiRange = cfi, quote = row.text, text = note, existing = note)
        }

        it("#332 TC-7: opens in new mode for a blank note") {
            checkAll(Exhaustive.of("", "   ", "\n\t")) { blank ->
                val cfi = randomCfi()
                val row = savedAt(cfi, randomEnum()).copy(note = blank)
                val vm = savedVm(row, highlights = repository())

                vm.noteFromTapOn(cfi)

                vm.uiState.value.noteComposer shouldBe NoteComposerState(cfiRange = cfi, quote = row.text)
            }
        }
    }

    describe("showing the note on the menu") {
        it("#332 TC-4: takes text and note from the row the page draws") {
            val cfi = randomCfi()
            val text = randomString()
            val vm = savedVm(savedAt(cfi, randomEnum()).copy(note = randomString()), highlights = repository())

            vm.selectText(cfi, text)
            vm.onHighlightColorChosen(randomEnum())
            vm.tap(hitOn(cfi))

            val entry = vm.uiState.value.highlightMenu.shouldNotBeNull().activeEntry
            entry.text shouldBe text
            entry.note.shouldBeNull()
            entry.hasNote shouldBe false
        }

        it("#332 TC-5: shows the new note on the next menu open") {
            checkAll(Exhaustive.enum<NoteOrigin>()) { origin ->
                val cfi = randomCfi()
                val note = randomString()
                val vm = openedOn(origin, cfi, "  $note  ", repository(savedId = randomLong()))

                vm.onNoteSaveRequested()
                vm.tap(hitOn(cfi))

                val entry = vm.uiState.value.highlightMenu.shouldNotBeNull().activeEntry
                entry.note shouldBe note
                entry.hasNote shouldBe true
            }
        }
    }

    describe("editing a note") {
        it("#332 TC-11: sends the trimmed text for the saved id") {
            val cfi = randomCfi()
            val row = savedAt(cfi, randomEnum()).copy(note = "old${randomString()}")
            val highlights = repository()
            val vm = savedVm(row, highlights = highlights)
            vm.noteFromTapOn(cfi)
            val changed = "new${randomString()}"

            vm.onNoteTextChanged("  $changed  ")
            vm.onNoteSaveRequested()

            highlights.notes shouldBe listOf(row.id to changed)
        }
    }

    describe("asking to delete a note") {
        it("#332 TC-12: asks before deleting an existing note, and not in new mode") {
            val editCfi = randomCfi()
            val editing = savedVm(savedAt(editCfi, randomEnum()).copy(note = randomString()), highlights = repository())
            editing.noteFromTapOn(editCfi)

            editing.onNoteDeleteRequested()

            editing.uiState.value.noteComposer.shouldNotBeNull().confirmingDelete shouldBe true

            val newCfi = randomCfi()
            val fresh = savedVm(savedAt(newCfi, randomEnum()), highlights = repository())
            fresh.noteFromTapOn(newCfi)

            fresh.onNoteDeleteRequested()

            fresh.uiState.value.noteComposer.shouldNotBeNull().confirmingDelete shouldBe false
        }

        it("#332 TC-13: keeps the note when the delete is cancelled") {
            val cfi = randomCfi()
            val note = randomString()
            val highlights = repository()
            val vm = savedVm(savedAt(cfi, randomEnum()).copy(note = note), highlights = highlights)
            vm.noteFromTapOn(cfi)
            vm.onNoteDeleteRequested()
            val asking = vm.uiState.value.noteComposer.shouldNotBeNull()

            vm.onNoteDeleteCancelled()

            vm.uiState.value.noteComposer shouldBe asking.copy(confirmingDelete = false)
            highlights.notes.shouldBeEmpty()
        }

        it("#332 TC-13: closing while the dialog is up only closes the dialog") {
            val cfi = randomCfi()
            val note = randomString()
            val highlights = repository()
            val vm = savedVm(savedAt(cfi, randomEnum()).copy(note = note), highlights = highlights)
            vm.noteFromTapOn(cfi)
            vm.onNoteDeleteRequested()

            vm.onNoteCloseRequested()

            val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
            composer.confirmingDelete shouldBe false
            composer.confirmingDiscard shouldBe false
            composer.text shouldBe note
            highlights.notes.shouldBeEmpty()
        }
    }

    describe("deleting a note") {
        it("#332 TC-14: sends a null note and closes") {
            checkAll(Exhaustive.enum<NoteOrigin>()) { origin ->
                val id = randomLong()
                val highlights = repository(savedId = id)
                val vm = editingOn(origin, randomCfi(), randomString(), id, highlights)

                vm.onNoteDeleteRequested()
                vm.onNoteDeleteConfirmed()

                highlights.notes.last() shouldBe (id to null)
                vm.uiState.value.noteComposer.shouldBeNull()
            }
        }

        it("#332 TC-15: removes the underline but keeps the highlight") {
            checkAll(Exhaustive.enum<NoteOrigin>()) { origin ->
                val cfi = randomCfi()
                val id = randomLong()
                val vm = editingOn(origin, cfi, randomString(), id, repository(savedId = id))
                val before = vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }

                vm.onNoteDeleteRequested()
                vm.onNoteDeleteConfirmed()

                val after = vm.uiState.value.pageHighlights.single { it.cfiRange.trim() == cfi }
                before.hasNote shouldBe true
                after.hasNote shouldBe false
                after.color shouldBe before.color

                vm.tap(hitOn(cfi))
                val entry = vm.uiState.value.highlightMenu.shouldNotBeNull().activeEntry
                entry.note.shouldBeNull()
                entry.hasNote shouldBe false
            }
        }

        it("#332 TC-16: marks deleting and ignores typing and close") {
            val cfi = randomCfi()
            val gate = CompletableDeferred<Unit>()
            val highlights = repository(noteGate = gate)
            val vm = savedVm(savedAt(cfi, randomEnum()).copy(note = randomString()), highlights = highlights)
            vm.noteFromTapOn(cfi)
            vm.onNoteDeleteRequested()
            vm.onNoteDeleteConfirmed()
            val inFlight = vm.uiState.value.noteComposer.shouldNotBeNull()

            vm.onNoteTextChanged("typed")
            vm.onNoteCloseRequested()

            inFlight.deleting shouldBe true
            inFlight.saving shouldBe false
            vm.uiState.value.noteComposer shouldBe inFlight
            gate.complete(Unit)
        }
    }

    describe("a delete that fails") {
        it("#332 TC-17: shows the delete error and keeps the note") {
            checkAll(Exhaustive.collection(allNoteErrors())) { error ->
                val cfi = randomCfi()
                val note = randomString()
                val vm = savedVm(
                    savedAt(cfi, randomEnum()).copy(note = note),
                    highlights = notesAnswering(HighlightNoteResult.Failed(error)),
                )
                vm.noteFromTapOn(cfi)

                vm.onNoteDeleteRequested()
                vm.onNoteDeleteConfirmed()

                val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
                composer.deleteFailed shouldBe true
                composer.deleting shouldBe false
                composer.failed shouldBe false
                composer.existing shouldBe note
                composer.text shouldBe note
            }
        }

        it("#332 TC-17: shows the delete error when the highlight has no server id") {
            val cfi = randomCfi()
            val note = randomString()
            val row = savedAt(cfi, randomEnum()).copy(note = note)
            val uuid = randomString()
            val highlights = repository()
            highlights.seed(uuid, HighlightsState.Loaded(listOf(row)))
            val vm = sut(bookUuid = uuid, highlightsRepository = highlights)
            vm.noteFromTapOn(cfi)
            highlights.seed(uuid, HighlightsState.Loaded(emptyList()))

            vm.onNoteDeleteRequested()
            vm.onNoteDeleteConfirmed()

            val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
            composer.deleteFailed shouldBe true
            composer.deleting shouldBe false
            highlights.notes.shouldBeEmpty()
        }

        it("#332 TC-18: clears the save error when Delete starts") {
            val cfi = randomCfi()
            val note = randomString()
            val vm = savedVm(
                savedAt(cfi, randomEnum()).copy(note = note),
                highlights = notesAnswering(HighlightNoteResult.Failed(HighlightError.Unreachable)),
            )
            vm.noteFromTapOn(cfi)
            vm.onNoteTextChanged("$note more")
            vm.onNoteSaveRequested()
            vm.uiState.value.noteComposer.shouldNotBeNull().failed shouldBe true

            vm.onNoteDeleteRequested()
            vm.onNoteDeleteConfirmed()

            val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
            composer.failed shouldBe false
            composer.deleteFailed shouldBe true
        }

        it("#332 TC-18: clears the delete error when Save starts") {
            val cfi = randomCfi()
            val note = randomString()
            val vm = savedVm(
                savedAt(cfi, randomEnum()).copy(note = note),
                highlights = notesAnswering(HighlightNoteResult.Failed(HighlightError.Unreachable)),
            )
            vm.noteFromTapOn(cfi)
            vm.onNoteDeleteRequested()
            vm.onNoteDeleteConfirmed()
            vm.uiState.value.noteComposer.shouldNotBeNull().deleteFailed shouldBe true

            vm.onNoteTextChanged("$note more")
            vm.onNoteSaveRequested()

            val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
            composer.deleteFailed shouldBe false
            composer.failed shouldBe true
        }
    }

    describe("closing an edited note") {
        it("#332 TC-19: closes at once when the note is unchanged after trimming") {
            checkAll(Exhaustive.of("  ", "\n", " \t")) { pad ->
                val cfi = randomCfi()
                val note = randomString()
                val vm = savedVm(savedAt(cfi, randomEnum()).copy(note = note), highlights = repository())
                vm.noteFromTapOn(cfi)
                vm.onNoteTextChanged(pad + note)

                vm.onNoteCloseRequested()

                vm.uiState.value.noteComposer.shouldBeNull()
            }
        }

        it("#332 TC-20: asks before discarding a changed note") {
            val cfi = randomCfi()
            val note = randomString()
            val vm = savedVm(savedAt(cfi, randomEnum()).copy(note = note), highlights = repository())
            vm.noteFromTapOn(cfi)
            vm.onNoteTextChanged("$note more")

            vm.onNoteCloseRequested()

            val composer = vm.uiState.value.noteComposer.shouldNotBeNull()
            composer.confirmingDiscard shouldBe true
            composer.text shouldBe "$note more"
        }
    }
})

/** Every way a note write can fail, so a failure test covers each one. */
private fun allNoteErrors(): List<HighlightError> = listOf(
    HighlightError.NoServer,
    HighlightError.Unreachable,
    HighlightError.SessionExpired,
    HighlightError.BookNotFound,
    HighlightError.Rejected,
    HighlightError.Http(404),
    HighlightError.Http(500),
    HighlightError.UnexpectedResponse,
)

/** A saved row at [cfi] in [color], with no note. */
private fun savedAt(cfi: String, color: HighlightColor): Highlight =
    randomHighlight().copy(location = cfi, color = color, note = null)

/**
 * A repository whose creates wait for [createGate], then answer with a saved row of [savedId] or a failure, and whose
 * note writes wait for [noteGate], then answer [note]. An open gate answers at once.
 */
private fun repository(
    createGate: CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) },
    savedId: Long = randomLong(),
    creates: Boolean = true,
    noteGate: CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) },
    note: HighlightNoteResult = HighlightNoteResult.Saved,
): TestHighlightsRepository = TestHighlightsRepository().apply {
    onAdd = { draft ->
        createGate.await()
        if (creates) {
            HighlightWriteResult.Saved(randomHighlight().copy(id = savedId, location = draft.cfiRange))
        } else {
            HighlightWriteResult.Failed(HighlightError.Unreachable)
        }
    }
    onNote = { _, _, _ ->
        noteGate.await()
        note
    }
}

/** A repository whose note writes answer [result] at once. */
private fun notesAnswering(result: HighlightNoteResult): TestHighlightsRepository =
    TestHighlightsRepository().apply { onNote = { _, _, _ -> result } }

/** A ViewModel for a book whose highlights read holds [rows]. */
private fun savedVm(vararg rows: Highlight, highlights: TestHighlightsRepository): ReaderViewModel {
    val uuid = randomString()
    highlights.seed(uuid, HighlightsState.Loaded(rows.toList()))
    return sut(bookUuid = uuid, highlightsRepository = highlights)
}

/** A ViewModel for a book with no saved highlights, whose session highlights are saved by [highlights]. */
private fun sessionVm(highlights: TestHighlightsRepository): ReaderViewModel {
    val uuid = randomString()
    highlights.seed(uuid, HighlightsState.Loaded(emptyList()))
    return sut(bookUuid = uuid, highlightsRepository = highlights)
}

/**
 * A ViewModel with the note screen open on a highlight of [origin] at [cfi], with [text] typed. A session highlight is
 * made from a selection first.
 */
private fun openedOn(
    origin: NoteOrigin,
    cfi: String,
    text: String,
    highlights: TestHighlightsRepository,
): ReaderViewModel {
    val vm = when (origin) {
        NoteOrigin.Saved -> savedVm(savedAt(cfi, randomEnum()), highlights = highlights).also { it.noteFromTapOn(cfi) }
        NoteOrigin.Session -> sessionVm(highlights).also {
            it.drawSession(cfi, randomEnum())
            it.onHighlightMenuAction(HighlightMenuAction.Note)
        }
    }
    vm.onNoteTextChanged(text)
    return vm
}

/** A saved highlight's note screen with [text] typed, and the close button pressed so the discard dialog is up. */
private fun openedWithTextAsking(text: String): ReaderViewModel {
    val cfi = randomCfi()
    val vm = savedVm(savedAt(cfi, randomEnum()), highlights = repository())
    vm.noteFromTapOn(cfi)
    vm.onNoteTextChanged(text)
    vm.onNoteCloseRequested()
    return vm
}

/**
 * A ViewModel with the note screen open in edit mode on a highlight of [origin] at [cfi], whose note is [note] and whose
 * server id is [savedId]. A session highlight has its note saved first, then is tapped again, as a saved one reads.
 */
private fun editingOn(
    origin: NoteOrigin,
    cfi: String,
    note: String,
    savedId: Long,
    highlights: TestHighlightsRepository,
): ReaderViewModel = when (origin) {
    NoteOrigin.Saved -> savedVm(savedAt(cfi, randomEnum()).copy(id = savedId, note = note), highlights = highlights)
        .also { it.noteFromTapOn(cfi) }
    NoteOrigin.Session -> openedOn(origin, cfi, note, highlights).also {
        it.onNoteSaveRequested()
        it.noteFromTapOn(cfi)
    }
}

/** Taps the highlight at [cfi] and chooses Note in its menu. */
private fun ReaderViewModel.noteFromTapOn(cfi: String) {
    tap(hitOn(cfi))
    onHighlightMenuAction(HighlightMenuAction.Note)
}

/** Selects [text] at [cfi] and chooses Note in the selection menu. */
private fun ReaderViewModel.noteFromSelection(cfi: String, text: String) {
    selectText(cfi, text)
    onSelectionMenuAction(HighlightMenuAction.Note)
}

/** A page tap that landed on [hits]: the first at the top level, as the glue reports it, and every hit under `hits`. */
private fun ReaderViewModel.tap(vararg hits: AnnotationHit) {
    val first = hits.first()
    val payload = AnnotationTapData(cfiRange = first.cfiRange, rects = first.rects, hits = hits.toList())
    onPageEvent(ReaderEvent.AnnotationTap(payload))
}

/** A hit on [cfi] with one random rect. */
private fun hitOn(cfi: String): AnnotationHit = AnnotationHit(cfi, listOf(randomPageRect()))

/** A live selection over [cfi] with [text]. */
private fun ReaderViewModel.selectText(cfi: String, text: String = randomString()) =
    onPageEvent(ReaderEvent.Selection(randomSelectionData().copy(cfiRange = cfi, text = text)))

/** A session highlight at [cfi] in [color], made from a selection and left open in the highlight menu. */
private fun ReaderViewModel.drawSession(cfi: String, color: HighlightColor) {
    selectText(cfi)
    onHighlightColorChosen(color)
    tap(hitOn(cfi))
}
