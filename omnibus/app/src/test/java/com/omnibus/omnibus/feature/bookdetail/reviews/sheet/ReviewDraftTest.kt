package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.randomReview
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class ReviewDraftTest : DescribeSpec({

    /** A review with every field set, so each draft below can change or clear any of them. */
    fun opened() = randomReviewFields().copy(rating = 4f, text = "Great", notes = "N", spoiler = false)

    describe("a review's fields") {
        it("TC-1: read blank writing as none") {
            val fields = ReviewFields.of(randomReview().copy(text = "   ", privateNotes = ""))

            fields.text shouldBe null
            fields.notes shouldBe null
        }
    }

    describe("the sheet's title") {
        it("TC-20: is Edit only for a review with writing, across each kind of opened review") {
            checkAll(
                Exhaustive.of(
                    randomReviewFields().copy(text = "x", notes = null) to ReviewSheetTitle.Edit,
                    randomReviewFields().copy(text = null, notes = "n") to ReviewSheetTitle.Edit,
                    randomReviewFields().copy(text = null, notes = null) to ReviewSheetTitle.Write,
                ),
            ) { (fields, title) ->
                titleFor(fields) shouldBe title
            }
        }
    }

    describe("whether a draft is dirty") {
        it("TC-2, TC-3: is clean for each draft that matches the opened review, touched in any way") {
            val opened = randomReviewFields()

            checkAll(
                Exhaustive.of(
                    ReviewDraft(),
                    ReviewDraft(rating = Patch.Set(opened.rating)),
                    ReviewDraft(text = Patch.Set(opened.text)),
                    ReviewDraft(notes = Patch.Set(opened.notes)),
                    ReviewDraft(spoiler = Patch.Set(opened.spoiler)),
                ),
            ) { draft ->
                draft.isDirty(opened) shouldBe false
            }
        }

        context("when the opened review has no writing") {
            it("TC-4: is clean for each blank value") {
                val opened = randomReviewFields().copy(text = null, notes = null)

                checkAll(
                    Exhaustive.of(
                        ReviewDraft(text = Patch.Set("   ")),
                        ReviewDraft(text = Patch.Set("")),
                        ReviewDraft(notes = Patch.Set("  ")),
                    ),
                ) { draft ->
                    draft.isDirty(opened) shouldBe false
                }
            }
        }

        it("TC-4, TC-8: is dirty for each changed field") {
            val opened = opened()

            checkAll(
                Exhaustive.of(
                    ReviewDraft(rating = Patch.Set(2.5f)),
                    ReviewDraft(text = Patch.Set("   ")),
                    ReviewDraft(notes = Patch.Set("Other")),
                    ReviewDraft(spoiler = Patch.Set(true)),
                ),
            ) { draft ->
                draft.isDirty(opened) shouldBe true
            }
        }
    }

    describe("the save patch") {
        it("TC-5, TC-6, TC-7, TC-8, TC-10: holds only the changed field, blank as null, for each kind of change") {
            val opened = opened()

            checkAll(
                Exhaustive.of(
                    ReviewDraft(rating = Patch.Set(2.5f)) to ReviewPatch(rating = Patch.Set(2.5f)),
                    ReviewDraft(rating = Patch.Set(null)) to ReviewPatch(rating = Patch.Set(null)),
                    ReviewDraft(text = Patch.Set(null)) to ReviewPatch(text = Patch.Set(null)),
                    ReviewDraft(text = Patch.Set("  ")) to ReviewPatch(text = Patch.Set(null)),
                    ReviewDraft(notes = Patch.Set(" \n")) to ReviewPatch(privateNotes = Patch.Set(null)),
                    ReviewDraft(spoiler = Patch.Set(true)) to ReviewPatch(isSpoiler = Patch.Set(true)),
                ),
            ) { (draft, patch) ->
                draft.savePatch(opened) shouldBe patch
            }
        }

        it("TC-9: leaves out a touched field that matches the server") {
            val server = randomReviewFields().copy(rating = 4f)

            ReviewDraft(rating = Patch.Set(3f), text = Patch.Set(server.text)).savePatch(server) shouldBe
                ReviewPatch(rating = Patch.Set(3f))
        }
    }

    describe("the displayed values") {
        it("TC-13: are the draft where touched, the latest server value elsewhere") {
            val server = randomReviewFields().copy(rating = 5f)

            ReviewDraft(rating = Patch.Set(3f)).displayed(server) shouldBe server.copy(rating = 3f)
        }
    }

    describe("the conflicts") {
        it("TC-14, TC-15: name every touched field the server changed, in display order") {
            val draft = ReviewDraft(
                rating = Patch.Set(3f),
                text = Patch.Set("Mine"),
                notes = Patch.Set("Mine notes"),
                spoiler = Patch.Set(false),
            )
            val server = ReviewFields(rating = 5f, text = "Theirs", notes = "Theirs notes", spoiler = true)

            draft.conflicts(opened(), server) shouldContainExactly listOf(
                ReviewField.Rating,
                ReviewField.Text,
                ReviewField.Notes,
                ReviewField.Spoiler,
            )
        }

        it("TC-16: leave out fields the reader did not touch") {
            val opened = opened()
            val server = opened.copy(rating = 5f, text = "Theirs")

            ReviewDraft(spoiler = Patch.Set(true)).conflicts(opened, server).shouldBeEmpty()
        }

        it("TC-17, TC-19: are none for each server that has not moved away from the reader") {
            val opened = opened()

            checkAll(Exhaustive.of(opened.copy(rating = 3f), opened)) { server ->
                ReviewDraft(rating = Patch.Set(3f)).conflicts(opened, server).shouldBeEmpty()
            }
        }
    }
})
