package com.omnibus.omnibus.data.reviews

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReviewPatchTest : DescribeSpec({

    /** A review with the given parts. Its attribution is the signed-in reader's, which is blank. */
    fun review(
        rating: Float? = null,
        text: String? = null,
        notes: String? = null,
        spoiler: Boolean = false,
        updatedAt: Long? = 1_000L,
    ) = Review(
        userId = 0L,
        displayName = "",
        hasAvatar = false,
        rating = rating,
        text = text,
        privateNotes = notes,
        isSpoiler = spoiler,
        updatedAtMillis = updatedAt,
    )

    describe("applying a patch") {

        it("TC-7: a set rating on no review makes a new one, and a change alters only the rating") {
            val created = (null as Review?).applying(ReviewPatch(rating = Patch.Set(4.5f)))
            created shouldBe Review(0L, "", false, 4.5f, null, null, isSpoiler = false, updatedAtMillis = null)

            val full = review(rating = 4f, text = "Body", notes = "private", spoiler = true, updatedAt = 99L)
            val changed = full.applying(ReviewPatch(rating = Patch.Set(3.5f)))
            changed shouldBe full.copy(rating = 3.5f)
            changed!!.updatedAtMillis shouldBe 99L
        }

        it("TC-9: clearing the rating keeps the text and the notes") {
            val withText = review(rating = 4f, text = "Body", updatedAt = 5L)
            withText.applying(ReviewPatch(rating = Patch.Set(null))) shouldBe withText.copy(rating = null)

            val withNotes = review(notes = "private")
            withNotes.applying(ReviewPatch(rating = Patch.Set(null))) shouldBe withNotes
        }

        it("TC-10: a review left with no rating, text or notes is null, and blank text counts as null") {
            review(rating = 4f).applying(ReviewPatch(rating = Patch.Set(null))) shouldBe null
            review(text = "Body").applying(ReviewPatch(text = Patch.Set(null))) shouldBe null
            review(text = "Body").applying(ReviewPatch(text = Patch.Set("  "))) shouldBe null
            review(text = "Body", notes = "private").applying(ReviewPatch(text = Patch.Set(null)))!!.let {
                it.text shouldBe null
                it.privateNotes shouldBe "private"
            }
            (null as Review?).applying(ReviewPatch(isSpoiler = Patch.Set(true))) shouldBe null
        }
    }

    describe("no-op patches") {

        it("TC-11: a patch that would leave the review as it is is a no-op") {
            val mine = review(rating = 4f, text = "Body", notes = "private", spoiler = true)
            val nothing = ReviewPatch()

            nothing.isNoOpFor(null) shouldBe true
            nothing.isNoOpFor(mine) shouldBe true
            ReviewPatch(rating = Patch.Set(4f)).isNoOpFor(mine) shouldBe true
            ReviewPatch(text = Patch.Set("Body")).isNoOpFor(mine) shouldBe true
            ReviewPatch(privateNotes = Patch.Set("private")).isNoOpFor(mine) shouldBe true
            ReviewPatch(isSpoiler = Patch.Set(true)).isNoOpFor(mine) shouldBe true
            ReviewPatch(rating = Patch.Set(null)).isNoOpFor(null) shouldBe true
            ReviewPatch(text = Patch.Set("  ")).isNoOpFor(null) shouldBe true
            ReviewPatch(rating = Patch.Set(null)).isNoOpFor(review(text = "Body")) shouldBe true

            ReviewPatch(rating = Patch.Set(4f)).isNoOpFor(null) shouldBe false
            ReviewPatch(rating = Patch.Set(null)).isNoOpFor(review(rating = 4f)) shouldBe false
        }
    }
})
