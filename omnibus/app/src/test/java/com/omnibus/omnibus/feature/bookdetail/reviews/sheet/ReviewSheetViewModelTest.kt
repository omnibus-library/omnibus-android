package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.bookprogress.TestBookProgressRepository
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.highlights.TestHighlightsRepository
import com.omnibus.omnibus.data.highlights.randomHighlight
import com.omnibus.omnibus.data.reviews.BookReviews
import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewError
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.ReviewWriteResult
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.data.reviews.TestReviewsRepository
import com.omnibus.omnibus.data.reviews.randomReview
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.reviews.mine.ReviewSavePulse
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain

/** A ViewModel on fresh fakes, which each test scripts. */
private class Rig {
    val uuid = randomString()
    val reviews = TestReviewsRepository()
    val highlights = TestHighlightsRepository()
    val pulse = ReviewSavePulse()
    val now = randomLong()
    val vm = ReviewSheetViewModel(uuid, reviews, highlights, TestBookProgressRepository(), pulse, TimeSource { now })

    fun seedMine(mine: Review?) = reviews.seed(uuid, ReviewsState.Loaded(BookReviews(mine, emptyList(), null)))

    /** Seeds the reviews as [mine] and opens the sheet on them. */
    fun openOn(mine: Review?): Rig {
        seedMine(mine)
        vm.open()
        return this
    }
}

/** A sheet opened on a review rated 4, with its rating changed to 3. */
private fun changedRig() = Rig().openOn(randomReview().copy(rating = 4f)).also { it.vm.setRating(3f) }

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewSheetViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("opening") {
        it("TC-32: does nothing for each state that is not loaded") {
            checkAll(Exhaustive.of(ReviewsState.Loading, ReviewsState.Offline, ReviewsState.Failed)) { state ->
                val rig = Rig()
                rig.reviews.seed(rig.uuid, state)

                rig.vm.open()

                rig.vm.uiState.value.open shouldBe false
            }
        }

        it("TC-33: shows the review's fields") {
            val mine = randomReview()

            Rig().openOn(mine).vm.uiState.value.shown shouldBe ReviewFields.of(mine)
        }

        context("when the sheet is already open") {
            it("TC-35: keeps the draft") {
                val rig = changedRig()
                rig.seedMine(randomReview().copy(rating = 5f))

                rig.vm.open()

                rig.vm.uiState.value.shown.rating shouldBe 3f
            }
        }
    }

    describe("the title") {
        context("when the server gains text after opening") {
            it("TC-36: stays as it was at opening") {
                val rig = Rig().openOn(randomReview().copy(text = null, privateNotes = null))

                rig.seedMine(randomReview().copy(text = randomString()))

                rig.vm.uiState.value.title shouldBe ReviewSheetTitle.Write
            }
        }
    }

    describe("the private notes") {
        it("TC-37: start expanded only for a review with notes, across each kind of notes") {
            checkAll(Exhaustive.of("N" to true, null to false, "  " to false)) { (notes, expanded) ->
                Rig().openOn(randomReview().copy(privateNotes = notes)).vm.uiState.value.notesExpanded shouldBe expanded
            }
        }
    }

    describe("the save button") {
        it("TC-38: is enabled only while the draft differs from the opened review") {
            val vm = Rig().openOn(randomReview().copy(rating = 4f)).vm
            val enabled = mutableListOf(vm.uiState.value.saveEnabled)

            vm.setRating(2.5f)
            enabled += vm.uiState.value.saveEnabled
            vm.setRating(4f)
            enabled += vm.uiState.value.saveEnabled

            enabled shouldBe listOf(false, true, false)
        }
    }

    describe("saving") {
        it("TC-40: sends the changed fields") {
            val rig = Rig().openOn(randomReview().copy(rating = 4f, privateNotes = "N"))
            rig.vm.setRating(3f)
            rig.vm.setNotes("Notes two")

            rig.vm.save()

            rig.reviews.updateCalls shouldContainExactly listOf(
                rig.uuid to ReviewPatch(rating = Patch.Set(3f), privateNotes = Patch.Set("Notes two")),
            )
        }

        context("when the draft is clean") {
            it("TC-41: leaves the sheet open") {
                val rig = Rig().openOn(randomReview())

                rig.vm.save()

                rig.vm.uiState.value.closing shouldBe false
            }
        }

        context("when a save is in flight") {
            it("TC-41: sends once") {
                val rig = changedRig()
                val gate = CompletableDeferred<Unit>()
                rig.reviews.writeGates += gate

                rig.vm.save()
                rig.vm.save()

                rig.reviews.updateCalls.size shouldBe 1
                gate.complete(Unit)
            }
        }

        it("TC-42, TC-43: closes the sheet, for each of a saved answer, an unchanged one") {
            checkAll(Exhaustive.of(ReviewWriteResult.Saved(randomReview()), ReviewWriteResult.Unchanged)) { result ->
                val rig = changedRig()
                rig.reviews.writeResults.addLast(result)

                rig.vm.save()

                rig.vm.uiState.value.closing shouldBe true
            }
        }

        context("when the rating changed") {
            it("TC-44: pulses once, for each of a new rating, a clear") {
                checkAll(Exhaustive.of(3f, null)) { rating ->
                    val rig = Rig().openOn(randomReview().copy(rating = 4f))
                    rig.vm.setRating(rating)

                    rig.vm.save()

                    rig.pulse.pulses(rig.uuid).value shouldBe 1
                }
            }
        }

        context("when the rating did not change") {
            it("TC-45: does not pulse") {
                val rig = Rig().openOn(randomReview())
                rig.vm.setText("${randomString()}!")

                rig.vm.save()

                rig.pulse.pulses(rig.uuid).value shouldBe 0
            }
        }

        context("when the save fails") {
            it("TC-46: keeps the draft in the open sheet") {
                val rig = changedRig()
                rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))

                rig.vm.save()

                rig.vm.uiState.value.closing shouldBe false
                rig.vm.uiState.value.shown.rating shouldBe 3f
            }

            it("TC-47: says whether it was offline, for each error") {
                checkAll(
                    Exhaustive.of(
                        ReviewError.NoServer to true,
                        ReviewError.Unreachable to true,
                        ReviewError.Http(500) to false,
                        ReviewError.NotLoaded to false,
                    ),
                ) { (error, offline) ->
                    val rig = changedRig()
                    rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(error))

                    rig.vm.save()

                    rig.vm.uiState.value.failure shouldBe ReviewSheetFailure(offline)
                }
            }

            context("when the reader edits the draft") {
                it("TC-48: clears the failure") {
                    val rig = changedRig()
                    rig.reviews.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))
                    rig.vm.save()

                    rig.vm.setText(randomString())

                    rig.vm.uiState.value.failure shouldBe null
                }
            }
        }
    }

    describe("the conflicts") {
        context("when the server changes a touched field") {
            it("TC-49: names the field") {
                val rig = changedRig()

                rig.seedMine(randomReview().copy(rating = 5f))

                rig.vm.uiState.value.conflicts shouldContainExactly listOf(ReviewField.Rating)
            }
        }

        context("when the server changes an untouched field") {
            it("TC-51: shows the server's value") {
                val rig = Rig().openOn(randomReview().copy(rating = 4f))
                rig.vm.setText(randomString())

                rig.seedMine(randomReview().copy(rating = 2f))

                rig.vm.uiState.value.shown.rating shouldBe 2f
            }
        }

        context("when the sheet's own save is in flight") {
            it("TC-52: are none") {
                val rig = changedRig()
                val gate = CompletableDeferred<Unit>()
                rig.reviews.writeGates += gate

                rig.vm.save()

                rig.vm.uiState.value.conflicts.shouldBeEmpty()
                gate.complete(Unit)
            }
        }
    }

    describe("closing") {
        context("when the draft has changes") {
            it("TC-54: asks to discard them") {
                val rig = changedRig()

                rig.vm.requestClose()

                rig.vm.uiState.value.confirmingDiscard shouldBe true
            }
        }

        context("when the draft was touched then reverted") {
            it("TC-55: closes at once") {
                val rig = changedRig()
                rig.vm.setRating(4f)

                rig.vm.requestClose()

                rig.vm.uiState.value.closing shouldBe true
            }
        }

        context("when the reader keeps editing") {
            it("TC-56: dismisses the confirmation") {
                val rig = changedRig()
                rig.vm.requestClose()

                rig.vm.keepEditing()

                rig.vm.uiState.value.confirmingDiscard shouldBe false
            }
        }

        context("when the reader discards") {
            it("TC-57: closes the sheet") {
                val rig = changedRig()
                rig.vm.requestClose()

                rig.vm.discard()

                rig.vm.uiState.value.closing shouldBe true
            }

            it("TC-57: reopens on the server's review") {
                val rig = changedRig()
                rig.vm.requestClose()
                rig.vm.discard()
                rig.vm.closed()
                val latest = randomReview()
                rig.seedMine(latest)

                rig.vm.open()

                rig.vm.uiState.value.shown shouldBe ReviewFields.of(latest)
            }
        }
    }

    describe("the highlight picker") {
        it("TC-58, TC-60: follows each state with no rows") {
            checkAll(
                Exhaustive.of(
                    HighlightsState.Loading to PickerContent.Loading,
                    HighlightsState.Failed to PickerContent.Failed,
                ),
            ) { (state, picker) ->
                val rig = Rig().openOn(randomReview())
                rig.highlights.seed(rig.uuid, state)

                rig.vm.openPicker()

                rig.vm.uiState.value.picker shouldBe picker
            }
        }

        context("when the highlights failed to load") {
            context("when a retry loads them") {
                it("TC-60: shows their rows") {
                    val rig = Rig().openOn(randomReview())
                    val text = randomString()
                    rig.highlights.seed(rig.uuid, HighlightsState.Failed)
                    rig.vm.openPicker()
                    rig.highlights.refreshResults.addLast(HighlightsState.Loaded(listOf(randomHighlight().copy(text = text))))

                    rig.vm.retryHighlights()

                    rig.vm.uiState.value.picker.shouldBeInstanceOf<PickerContent.Loaded>().rows.map { it.text } shouldBe listOf(text)
                }
            }
        }

        context("when a highlight is picked") {
            /** A sheet on a review with the body "My take", with the highlight quoting "quote" picked. */
            fun picked(): Rig {
                val rig = Rig().openOn(randomReview().copy(text = "My take"))
                val highlight = randomHighlight().copy(text = "quote")
                rig.highlights.seed(rig.uuid, HighlightsState.Loaded(listOf(highlight)))
                rig.vm.openPicker()
                rig.vm.pickHighlight(highlight.id)
                return rig
            }

            it("TC-62: quotes it before the body") {
                picked().vm.uiState.value.shown.text shouldBe "> quote\n\nMy take"
            }

            it("TC-62: closes the picker") {
                picked().vm.uiState.value.pickerOpen shouldBe false
            }
        }
    }
})
