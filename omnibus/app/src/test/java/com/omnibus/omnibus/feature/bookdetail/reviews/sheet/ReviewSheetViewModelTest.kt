package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.bookprogress.FakeBookProgressRepository
import com.omnibus.omnibus.data.highlights.FakeHighlightsRepository
import com.omnibus.omnibus.data.highlights.Highlight
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.FakeReviewsRepository
import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewError
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.ReviewWriteResult
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.reviews.mine.ReviewSavePulse
import com.omnibus.omnibus.testing.CollectingScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewSheetViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    val uuid = "uuid-1"

    /** A review with the given parts, as the signed-in reader's, with no change time. */
    fun review(
        rating: Float? = null,
        text: String? = null,
        notes: String? = null,
        spoiler: Boolean = false,
    ) = Review(0L, "", false, rating, text, notes, isSpoiler = spoiler, updatedAtMillis = null)

    /** A Loaded state whose own review is [mine]. */
    fun loaded(mine: Review?) = ReviewsState.Loaded(BookReviews(mine, emptyList(), null))

    /** A highlight with the given passage and no placement. */
    fun highlight(id: Long, text: String?) =
        Highlight(id, text, null, HighlightColor.Amber, location = null, format = null, createdAtMillis = 1_000L)

    /** The rows the picker shows, as (id, text) pairs. Empty while the picker is not loaded. */
    fun rows(state: ReviewSheetUIState): List<Pair<Long, String?>> {
        val loaded = state.picker as? PickerContent.Loaded ?: return emptyList()
        return loaded.rows.map { it.id to it.text }
    }

    /** A ViewModel on fresh fakes. The fakes are public so each test can script them. */
    class Rig(val uuid: String) {
        val reviews = FakeReviewsRepository()
        val highlights = FakeHighlightsRepository()
        val progress = FakeBookProgressRepository()
        val pulse = ReviewSavePulse()
        val vm = ReviewSheetViewModel(uuid, reviews, highlights, progress, pulse, TimeSource { 1_000L })

        /** Seeds the reviews as [mine] (Loaded) and opens the sheet on them. */
        fun openOn(mine: Review?): Rig {
            reviews.seed(uuid, ReviewsState.Loaded(BookReviews(mine, emptyList(), null)))
            vm.open()
            return this
        }
    }

    describe("open") {

        it("TC-32: open does nothing unless the reviews are Loaded, and a later open after Loaded works") {
            val rig = Rig(uuid)
            listOf<ReviewsState>(ReviewsState.Loading, ReviewsState.Offline, ReviewsState.Failed).forEach { state ->
                rig.reviews.seed(uuid, state)
                rig.vm.open()
                rig.vm.setText("typed while closed")
                rig.vm.uiState.value.open shouldBe false
            }

            rig.reviews.seed(uuid, loaded(review(4f, "Great", "N")))
            rig.vm.open()
            rig.vm.uiState.value.open shouldBe true
            rig.vm.uiState.value.shown.text shouldBe "Great"
        }

        it("TC-33: open prefills the sheet from the review, with save disabled and no conflicts") {
            val state = Rig(uuid).openOn(review(4f, "Great", "N", spoiler = true)).vm.uiState.value

            state.open shouldBe true
            state.shown shouldBe ReviewFields(4f, "Great", "N", true)
            state.saveEnabled shouldBe false
            state.conflicts.shouldBeEmpty()
        }

        it("TC-34: open with no review starts empty and titled Write") {
            val state = Rig(uuid).openOn(null).vm.uiState.value

            state.shown shouldBe ReviewFields.Empty
            state.title shouldBe ReviewSheetTitle.Write
        }

        it("TC-35: a second open while the sheet is open keeps the draft and does not re-take the opened review") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)
            rig.vm.setText("Edited")

            rig.reviews.seed(uuid, loaded(review(5f, "Server", "N")))
            rig.vm.open()

            val state = rig.vm.uiState.value
            state.shown.rating shouldBe 3f
            state.shown.text shouldBe "Edited"
            // Had the second open re-taken the opened review, the draft would now look clean and conflict-free.
            state.conflicts shouldContainExactly listOf(ReviewField.Rating, ReviewField.Text)
        }
    }

    describe("title and notes") {

        it("TC-36: the title is chosen at open and does not change while typing or when the server gains text") {
            fun titleOn(mine: Review?): ReviewSheetTitle = Rig(uuid).openOn(mine).vm.uiState.value.title

            titleOn(review(text = "Great")) shouldBe ReviewSheetTitle.Edit
            titleOn(review(notes = "N")) shouldBe ReviewSheetTitle.Edit
            titleOn(review(4f)) shouldBe ReviewSheetTitle.Write
            titleOn(null) shouldBe ReviewSheetTitle.Write

            val rig = Rig(uuid).openOn(null)
            rig.vm.setText("typed")
            rig.vm.uiState.value.title shouldBe ReviewSheetTitle.Write
            rig.reviews.seed(uuid, loaded(review(text = "Server now")))
            rig.vm.uiState.value.title shouldBe ReviewSheetTitle.Write
        }

        it("TC-37: private notes start expanded only when the opened review has notes, and expandNotes shows them") {
            Rig(uuid).openOn(review(text = "Great", notes = "N")).vm.uiState.value.notesExpanded shouldBe true
            Rig(uuid).openOn(review(text = "Great")).vm.uiState.value.notesExpanded shouldBe false
            Rig(uuid).openOn(review(text = "Great", notes = "  ")).vm.uiState.value.notesExpanded shouldBe false

            val rig = Rig(uuid).openOn(review(text = "Great"))
            rig.vm.expandNotes()
            rig.vm.uiState.value.notesExpanded shouldBe true
        }
    }

    describe("editing") {

        it("TC-38: save is enabled while the draft differs, and touching a field back to its opened value disables it") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            val enabled = mutableListOf<Boolean>()

            enabled += rig.vm.uiState.value.saveEnabled
            rig.vm.setRating(2.5f)
            enabled += rig.vm.uiState.value.saveEnabled
            rig.vm.setRating(4f)
            enabled += rig.vm.uiState.value.saveEnabled
            rig.vm.setText("x")
            enabled += rig.vm.uiState.value.saveEnabled
            rig.vm.setText("Great")
            enabled += rig.vm.uiState.value.saveEnabled
            rig.vm.setSpoiler(true)
            enabled += rig.vm.uiState.value.saveEnabled

            enabled shouldBe listOf(false, true, false, true, false, true)
        }

        it("TC-39: each keystroke is in uiState at once") {
            val rig = Rig(uuid).openOn(null)

            rig.vm.setText("a")
            rig.vm.uiState.value.shown.text shouldBe "a"
            rig.vm.setText("ab")
            rig.vm.uiState.value.shown.text shouldBe "ab"
            rig.vm.setText("abc")
            rig.vm.uiState.value.shown.text shouldBe "abc"
        }
    }

    describe("save") {

        it("TC-40: save sends only the fields that changed") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)
            rig.vm.setNotes("Notes two")

            rig.vm.save()

            rig.reviews.updateCalls shouldContainExactly listOf(
                uuid to ReviewPatch(rating = Patch.Set(3f), privateNotes = Patch.Set("Notes two")),
            )
        }

        it("TC-41: save does nothing while clean, and while a save is in flight it sends once") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.save()
            rig.reviews.updateCalls.shouldBeEmpty()

            val gate = CompletableDeferred<Unit>()
            rig.reviews.writeGates += gate
            rig.vm.setRating(3f)
            rig.vm.save()
            rig.vm.save()

            rig.reviews.updateCalls.size shouldBe 1
            rig.vm.uiState.value.saving shouldBe true
            rig.vm.uiState.value.saveEnabled shouldBe false

            gate.complete(Unit)
            rig.vm.uiState.value.closing shouldBe true
        }

        it("TC-42: a saved review closes the sheet, and closed() ends the session with no failure left") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)

            rig.vm.save()

            rig.vm.uiState.value.closing shouldBe true
            rig.vm.uiState.value.saveEnabled shouldBe false
            rig.vm.uiState.value.open shouldBe true

            rig.vm.closed()

            rig.vm.uiState.value.open shouldBe false
            rig.vm.uiState.value.failure shouldBe null
        }

        it("TC-43: an Unchanged result closes the sheet and does not bump the pulse") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.reviews.writeResults.addLast(ReviewWriteResult.Unchanged)
            rig.vm.setRating(3f)

            rig.vm.save()

            rig.vm.uiState.value.closing shouldBe true
            rig.pulse.pulses(uuid).value shouldBe 0
        }

        it("TC-44: a saved rating, including a cleared one, bumps the pulse once") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(null)

            rig.vm.save()

            rig.pulse.pulses(uuid).value shouldBe 1
        }

        it("TC-45: a save without a rating change does not bump the pulse") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setText("Changed")
            rig.vm.setNotes("Other notes")
            rig.vm.setSpoiler(true)

            rig.vm.save()

            rig.vm.uiState.value.closing shouldBe true
            rig.pulse.pulses(uuid).value shouldBe 0
        }

        it("TC-46: a failed save keeps the draft, the sheet open, and a non-offline failure") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
            rig.vm.setRating(3f)
            rig.vm.setText("typed")
            rig.vm.setNotes("notes")
            rig.vm.setSpoiler(true)

            rig.vm.save()

            val state = rig.vm.uiState.value
            state.open shouldBe true
            state.closing shouldBe false
            state.shown shouldBe ReviewFields(3f, "typed", "notes", true)
            state.saving shouldBe false
            state.failure shouldBe ReviewSheetFailure(offline = false)
            rig.pulse.pulses(uuid).value shouldBe 0
        }

        it("TC-47: a failure with no connection is offline, and NotLoaded is a generic failure") {
            val offline = listOf(ReviewError.NoServer, ReviewError.Unreachable)
            offline.forEach { error ->
                val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
                rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(error))
                rig.vm.setRating(3f)

                rig.vm.save()

                rig.vm.uiState.value.failure shouldBe ReviewSheetFailure(offline = true)
            }

            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.NotLoaded))
            rig.vm.setRating(3f)

            rig.vm.save()

            rig.vm.uiState.value.failure shouldBe ReviewSheetFailure(offline = false)
        }

        it("TC-48: after a failure, an edit clears it, and a retry sends the patch, closes, and pulses once") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
            rig.vm.setRating(3f)
            rig.vm.save()
            rig.vm.uiState.value.failure shouldBe ReviewSheetFailure(offline = false)

            rig.vm.setText("more")
            rig.vm.uiState.value.failure shouldBe null

            rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.NoServer))
            rig.vm.save()
            rig.vm.uiState.value.failure shouldBe ReviewSheetFailure(offline = true)

            val gate = CompletableDeferred<Unit>()
            rig.reviews.writeGates += gate
            rig.vm.save()
            // The retry clears the old failure while it is in flight.
            rig.vm.uiState.value.failure shouldBe null
            rig.vm.uiState.value.saving shouldBe true
            gate.complete(Unit)

            rig.vm.uiState.value.closing shouldBe true
            rig.reviews.updateCalls.last() shouldBe (uuid to ReviewPatch(
                rating = Patch.Set(3f),
                text = Patch.Set("more"),
            ))
            rig.pulse.pulses(uuid).value shouldBe 1
        }
    }

    describe("conflicts") {

        it("TC-49: a touched field the server changes elsewhere is a conflict, shows the reader's value, and saves it") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)

            rig.reviews.seed(uuid, loaded(review(5f, "Great", "N")))

            val state = rig.vm.uiState.value
            state.conflicts shouldContainExactly listOf(ReviewField.Rating)
            state.shown.rating shouldBe 3f

            rig.vm.save()
            rig.reviews.updateCalls shouldContainExactly listOf(uuid to ReviewPatch(rating = Patch.Set(3f)))
        }

        it("TC-50: several touched fields the server changes give every conflict, in display order") {
            val both = Rig(uuid).openOn(review(4f, "Great", "N"))
            both.vm.setRating(3f)
            both.vm.setText("Mine")
            both.reviews.seed(uuid, loaded(review(5f, "Theirs", "N")))
            both.vm.uiState.value.conflicts shouldContainExactly listOf(ReviewField.Rating, ReviewField.Text)

            val textAndNotes = Rig(uuid).openOn(review(4f, "Great", "N"))
            textAndNotes.vm.setText("Mine")
            textAndNotes.vm.setNotes("Mine notes")
            textAndNotes.reviews.seed(uuid, loaded(review(4f, "Theirs", "Theirs notes")))
            textAndNotes.vm.uiState.value.conflicts shouldContainExactly listOf(ReviewField.Text, ReviewField.Notes)
        }

        it("TC-51: an untouched field follows the server and is left out of the save") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setText("Mine")

            rig.reviews.seed(uuid, loaded(review(2f, "Great", "N")))

            val state = rig.vm.uiState.value
            state.conflicts.shouldBeEmpty()
            state.shown.rating shouldBe 2f

            rig.vm.save()
            rig.reviews.updateCalls shouldContainExactly listOf(uuid to ReviewPatch(text = Patch.Set("Mine")))
        }

        it("TC-52: the sheet's own optimistic save is not a conflict, while the save is in flight or after it") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)
            val gate = CompletableDeferred<Unit>()
            rig.reviews.writeGates += gate

            rig.vm.save()
            rig.vm.uiState.value.conflicts.shouldBeEmpty()
            rig.vm.uiState.value.closing shouldBe false

            gate.complete(Unit)
            rig.vm.uiState.value.conflicts.shouldBeEmpty()
            rig.vm.uiState.value.closing shouldBe true
        }

        it("TC-53: a conflict clears when the server reaches the reader's value, or when the reader types the server's value") {
            val converged = Rig(uuid).openOn(review(4f, "Great", "N"))
            converged.vm.setRating(3f)
            converged.reviews.seed(uuid, loaded(review(5f, "Great", "N")))
            converged.vm.uiState.value.conflicts shouldContainExactly listOf(ReviewField.Rating)
            converged.reviews.seed(uuid, loaded(review(3f, "Great", "N")))
            converged.vm.uiState.value.conflicts.shouldBeEmpty()

            val retyped = Rig(uuid).openOn(review(4f, "Great", "N"))
            retyped.vm.setRating(3f)
            retyped.reviews.seed(uuid, loaded(review(5f, "Great", "N")))
            retyped.vm.uiState.value.conflicts shouldContainExactly listOf(ReviewField.Rating)
            retyped.vm.setRating(5f)
            retyped.vm.uiState.value.conflicts.shouldBeEmpty()
        }
    }

    describe("closing") {

        it("TC-54: closing with a changed draft asks to discard, and the sheet stays open") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)

            rig.vm.requestClose()

            val state = rig.vm.uiState.value
            state.confirmingDiscard shouldBe true
            state.open shouldBe true
            state.closing shouldBe false
        }

        it("TC-55: closing a clean draft, even one touched and reverted, closes at once with no write") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)
            rig.vm.setRating(4f)

            rig.vm.requestClose()

            val state = rig.vm.uiState.value
            state.closing shouldBe true
            state.confirmingDiscard shouldBe false
            rig.reviews.updateCalls.shouldBeEmpty()
        }

        it("TC-56: keep editing dismisses the confirmation and keeps the draft") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)
            rig.vm.requestClose()

            rig.vm.keepEditing()

            val state = rig.vm.uiState.value
            state.confirmingDiscard shouldBe false
            state.open shouldBe true
            state.shown.rating shouldBe 3f
        }

        it("TC-57: discard closes without writing, and a reopen starts from the server's review") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(3f)
            rig.vm.requestClose()

            rig.vm.discard()

            rig.vm.uiState.value.closing shouldBe true
            rig.vm.uiState.value.confirmingDiscard shouldBe false
            rig.reviews.updateCalls.shouldBeEmpty()

            rig.vm.closed()
            rig.reviews.seed(uuid, loaded(review(2f, "New", null)))
            rig.vm.open()

            val state = rig.vm.uiState.value
            state.shown shouldBe ReviewFields(2f, "New", null, false)
            state.saveEnabled shouldBe false
        }
    }

    describe("highlight picker") {

        it("TC-58: the picker shows Loading, then the highlights that have text") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))

            rig.vm.openPicker()
            rig.vm.uiState.value.pickerOpen shouldBe true
            rig.vm.uiState.value.picker shouldBe PickerContent.Loading

            rig.highlights.seed(
                uuid,
                HighlightsState.Loaded(listOf(highlight(1L, "First"), highlight(2L, null), highlight(3L, "Third"))),
            )
            rows(rig.vm.uiState.value) shouldContainExactly listOf(1L to "First", 3L to "Third")
        }

        it("TC-59: the chapters are fetched once across reopens of the picker") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))

            rig.vm.openPicker()
            rig.vm.closePicker()
            rig.vm.openPicker()

            rig.progress.chaptersCalls shouldContainExactly listOf(uuid)
        }

        it("TC-60: a failed picker shows Failed, and a retry that loads shows the rows") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.highlights.seed(uuid, HighlightsState.Failed)
            rig.vm.openPicker()
            rig.vm.uiState.value.picker shouldBe PickerContent.Failed

            rig.vm.retryHighlights()
            rig.vm.uiState.value.picker shouldBe PickerContent.Failed
            rig.highlights.refreshCalls shouldContainExactly listOf(uuid)

            rig.highlights.refreshResults.addLast(HighlightsState.Loaded(listOf(highlight(4L, "Back"))))
            rig.vm.retryHighlights()
            rows(rig.vm.uiState.value) shouldContainExactly listOf(4L to "Back")
            rig.highlights.refreshCalls shouldContainExactly listOf(uuid, uuid)
        }

        it("TC-61: highlights with no text, or none at all, give an empty picker") {
            val textless = Rig(uuid).openOn(review(4f, "Great", "N"))
            textless.highlights.seed(uuid, HighlightsState.Loaded(listOf(highlight(1L, null))))
            textless.vm.openPicker()
            textless.vm.uiState.value.picker shouldBe PickerContent.Loaded(emptyList())

            val none = Rig(uuid).openOn(review(4f, "Great", "N"))
            none.highlights.seed(uuid, HighlightsState.Loaded(emptyList()))
            none.vm.openPicker()
            none.vm.uiState.value.picker shouldBe PickerContent.Loaded(emptyList())
        }

        it("TC-62: picking a highlight quotes it before the body and closes the picker") {
            val withBody = Rig(uuid).openOn(review(4f, "My take", null))
            withBody.highlights.seed(uuid, HighlightsState.Loaded(listOf(highlight(7L, "quote"))))
            withBody.vm.openPicker()

            withBody.vm.pickHighlight(7L)

            val state = withBody.vm.uiState.value
            state.shown.text shouldBe "> quote\n\nMy take"
            state.pickerOpen shouldBe false
            state.saveEnabled shouldBe true

            val empty = Rig(uuid).openOn(review(4f, null, null))
            empty.highlights.seed(uuid, HighlightsState.Loaded(listOf(highlight(7L, "quote"))))
            empty.vm.openPicker()

            empty.vm.pickHighlight(7L)

            empty.vm.uiState.value.shown.text shouldBe "> quote\n\n"
            empty.vm.uiState.value.saveEnabled shouldBe true
        }
    }

    describe("state") {

        it("TC-63: the flags and the draft survive a new collector, because they live in the ViewModel") {
            val rig = Rig(uuid).openOn(review(4f, "Great", null))
            val collecting = CollectingScope()
            collecting.collect(rig.vm.uiState)
            rig.vm.expandNotes()
            rig.vm.setText("typed")
            rig.vm.openPicker()
            rig.vm.requestClose()
            collecting.cancel()

            val again = CollectingScope()
            again.collect(rig.vm.uiState)
            val state = rig.vm.uiState.value
            state.notesExpanded shouldBe true
            state.pickerOpen shouldBe true
            state.confirmingDiscard shouldBe true
            state.shown.text shouldBe "typed"
            again.cancel()
        }

        it("TC-64: a saved review reaches the shared reviews flow the card and the stage read") {
            val rig = Rig(uuid).openOn(review(4f, "Great", "N"))
            rig.vm.setRating(5f)
            rig.vm.setText("New")

            rig.vm.save()

            val shared = rig.reviews.getReviewsFlow(uuid).first() as ReviewsState.Loaded
            shared.reviews.mine?.rating shouldBe 5f
            shared.reviews.mine?.text shouldBe "New"
        }
    }
})
