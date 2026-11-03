package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.Review
import com.omnibus.omnibus.data.reviews.ReviewPatch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class ReviewDraftTest : DescribeSpec({

    /** A review with the given parts, as the signed-in reader's, with no change time. */
    fun review(
        rating: Float? = null,
        text: String? = null,
        notes: String? = null,
        spoiler: Boolean = false,
    ) = Review(0L, "", false, rating, text, notes, isSpoiler = spoiler, updatedAtMillis = null)

    /** The fixture the plan's draft cases start from: rated 4, text "Great", notes "N", not a spoiler. */
    val opened = ReviewFields(rating = 4f, text = "Great", notes = "N", spoiler = false)

    describe("ReviewFields") {

        it("TC-1: fields of review normalise blank text and notes, and keep the rest verbatim") {
            ReviewFields.of(null) shouldBe ReviewFields(null, null, null, false)
            ReviewFields.of(review(text = "   ", notes = "")) shouldBe ReviewFields(null, null, null, false)
            ReviewFields.of(review(rating = 3f, text = "  Keep  ", notes = "\t", spoiler = true)) shouldBe
                ReviewFields(3f, "  Keep  ", null, true)
        }

        it("TC-20: the title is Edit only when the review has text or notes") {
            ReviewFields(null, text = "x", notes = null, spoiler = false).hasWriting shouldBe true
            ReviewFields(null, text = null, notes = "n", spoiler = false).hasWriting shouldBe true
            ReviewFields(null, text = "x", notes = "n", spoiler = false).hasWriting shouldBe true
            ReviewFields.Empty.hasWriting shouldBe false
            ReviewFields.of(review(text = " ", notes = "  ")).hasWriting shouldBe false
            ReviewFields(4.5f, text = null, notes = null, spoiler = true).hasWriting shouldBe false

            titleFor(ReviewFields(null, text = "x", notes = null, spoiler = false)) shouldBe ReviewSheetTitle.Edit
            titleFor(ReviewFields(null, text = null, notes = "n", spoiler = false)) shouldBe ReviewSheetTitle.Edit
            titleFor(ReviewFields.Empty) shouldBe ReviewSheetTitle.Write
            titleFor(ReviewFields.of(review(text = " ", notes = "  "))) shouldBe ReviewSheetTitle.Write
            titleFor(ReviewFields(4.5f, text = null, notes = null, spoiler = true)) shouldBe ReviewSheetTitle.Write
        }
    }

    describe("dirty tracking") {

        it("TC-2: an untouched draft is clean") {
            ReviewDraft().isDirty(opened) shouldBe false
        }

        it("TC-3: touching a field and typing it back to the opened value is not dirty") {
            ReviewDraft(rating = Patch.Set(4f)).isDirty(opened) shouldBe false
            ReviewDraft(text = Patch.Set("Great")).isDirty(opened) shouldBe false
            ReviewDraft(spoiler = Patch.Set(false)).isDirty(opened) shouldBe false
            ReviewDraft(
                rating = Patch.Set(4f),
                text = Patch.Set("Great"),
                notes = Patch.Set("N"),
                spoiler = Patch.Set(false),
            ).isDirty(opened) shouldBe false
        }

        it("TC-4: blank equals null for dirty tracking") {
            val empty = ReviewFields.Empty
            ReviewDraft(text = Patch.Set("   ")).isDirty(empty) shouldBe false
            ReviewDraft(text = Patch.Set("")).isDirty(empty) shouldBe false
            ReviewDraft(text = Patch.Set(null)).isDirty(empty) shouldBe false
            ReviewDraft(notes = Patch.Set("  ")).isDirty(empty) shouldBe false
            ReviewDraft(notes = Patch.Set(null)).isDirty(empty) shouldBe false
            ReviewDraft(text = Patch.Set("   ")).isDirty(opened) shouldBe true
        }

        it("TC-5: clearing the text is dirty and saves null") {
            val draft = ReviewDraft(text = Patch.Set(null))
            draft.isDirty(opened) shouldBe true
            draft.savePatch(opened) shouldBe ReviewPatch(text = Patch.Set(null))
        }

        it("TC-6: clearing the notes is dirty and saves null, with nothing else in the patch") {
            val draft = ReviewDraft(notes = Patch.Set(""))
            draft.isDirty(opened) shouldBe true
            draft.savePatch(opened) shouldBe ReviewPatch(privateNotes = Patch.Set(null))
        }

        it("TC-7: a spoiler-only change is dirty and patches only the spoiler") {
            val draft = ReviewDraft(spoiler = Patch.Set(true))
            draft.isDirty(opened) shouldBe true
            draft.savePatch(opened) shouldBe ReviewPatch(isSpoiler = Patch.Set(true))
        }

        it("TC-8: rating clear, change and first rating are each dirty and patch the rating") {
            ReviewDraft(rating = Patch.Set(null)).let {
                it.isDirty(opened) shouldBe true
                it.savePatch(opened) shouldBe ReviewPatch(rating = Patch.Set(null))
            }
            ReviewDraft(rating = Patch.Set(2.5f)).let {
                it.isDirty(opened) shouldBe true
                it.savePatch(opened) shouldBe ReviewPatch(rating = Patch.Set(2.5f))
            }
            ReviewDraft(rating = Patch.Set(4.5f)).let {
                it.isDirty(ReviewFields.Empty) shouldBe true
                it.savePatch(ReviewFields.Empty) shouldBe ReviewPatch(rating = Patch.Set(4.5f))
            }
        }
    }

    describe("save patch") {

        it("TC-9: the patch holds only the touched fields that differ from the server") {
            val draft = ReviewDraft(rating = Patch.Set(3f), text = Patch.Set("Great"))
            draft.savePatch(opened) shouldBe ReviewPatch(rating = Patch.Set(3f))
        }

        it("TC-10: blank text and notes are sent as null, never as blank strings") {
            val draft = ReviewDraft(text = Patch.Set("  "), notes = Patch.Set(" \n"))
            draft.savePatch(opened) shouldBe ReviewPatch(text = Patch.Set(null), privateNotes = Patch.Set(null))
        }

        it("TC-11: a draft that has been reverted saves an empty patch") {
            val draft = ReviewDraft(
                rating = Patch.Set(4f),
                text = Patch.Set("Great"),
                notes = Patch.Set("N"),
                spoiler = Patch.Set(false),
            )
            draft.savePatch(opened) shouldBe ReviewPatch()
        }

        it("TC-12: a touched field the server changed saves the reader's value") {
            val server = opened.copy(rating = 5f)
            ReviewDraft(rating = Patch.Set(3f)).savePatch(server) shouldBe ReviewPatch(rating = Patch.Set(3f))
        }
    }

    describe("displayed values") {

        it("TC-13: touched fields show the draft and untouched fields show the latest server value") {
            val server = ReviewFields(rating = 5f, text = "New text", notes = "Server notes", spoiler = true)
            ReviewDraft(rating = Patch.Set(3f)).displayed(server) shouldBe
                ReviewFields(rating = 3f, text = "New text", notes = "Server notes", spoiler = true)
        }
    }

    describe("conflicts") {

        it("TC-14: one touched field the server changed is a conflict") {
            val server = opened.copy(rating = 5f)
            ReviewDraft(rating = Patch.Set(3f)).conflicts(opened, server) shouldContainExactly listOf(ReviewField.Rating)
        }

        it("TC-15: several conflicts come back in display order") {
            val draft = ReviewDraft(
                rating = Patch.Set(3f),
                text = Patch.Set("Mine"),
                notes = Patch.Set("Mine notes"),
                spoiler = Patch.Set(false),
            )
            val server = ReviewFields(rating = 5f, text = "Theirs", notes = "Theirs notes", spoiler = true)
            draft.conflicts(opened, server) shouldContainExactly listOf(
                ReviewField.Rating,
                ReviewField.Text,
                ReviewField.Notes,
                ReviewField.Spoiler,
            )
        }

        it("TC-16: fields the reader did not touch never conflict") {
            val server = opened.copy(rating = 5f, text = "Theirs")
            ReviewDraft(spoiler = Patch.Set(true)).conflicts(opened, server).shouldBeEmpty()
        }

        it("TC-17: the server equal to the draft, or equal to the opened review, is not a conflict") {
            ReviewDraft(rating = Patch.Set(3f)).conflicts(opened, opened.copy(rating = 3f)).shouldBeEmpty()
            ReviewDraft(rating = Patch.Set(3f)).conflicts(opened, opened).shouldBeEmpty()
        }

        it("TC-18: conflicts compare normalised values, so a blank server text is no text") {
            val server = ReviewFields.of(review(text = "   "))
            ReviewDraft(text = Patch.Set("   ")).conflicts(ReviewFields.Empty, server).shouldBeEmpty()
        }

        it("TC-19: a conflict clears when the server converges to the reader's value or back to the opened value") {
            val draft = ReviewDraft(rating = Patch.Set(3f))
            draft.conflicts(opened, opened.copy(rating = 3f)).shouldBeEmpty()
            draft.conflicts(opened, opened).shouldBeEmpty()
        }
    }
})
