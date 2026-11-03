package com.omnibus.omnibus.data.reviews

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class FakeReviewsRepositoryTest : DescribeSpec({

    val uuid = "book-1"

    /** A Loaded state whose reviews are [mine] and nothing else. */
    fun loaded(rating: Float?, updatedAt: Long = 100L) = ReviewsState.Loaded(
        BookReviews(
            mine = Review(0L, "", false, rating, null, null, isSpoiler = false, updatedAtMillis = updatedAt),
            others = emptyList(),
            summary = null,
        ),
    )

    /** The reviews' own rating for [uuid], as the fake's flow shows it. */
    suspend fun FakeReviewsRepository.ratingNow(): Float? =
        ((getReviewsFlow(uuid).first() as ReviewsState.Loaded).reviews.mine)?.rating

    describe("writes") {

        it("TC-34: updates and deletes are recorded in call order, apply at once, and take scripted results") {
            val fake = FakeReviewsRepository()
            fake.seed(uuid, loaded(4f))

            fake.updateMyReview(uuid, ReviewPatch(rating = Patch.Set(4.5f))) shouldBe
                ReviewWriteResult.Saved(Review(0L, "", false, 4.5f, null, null, isSpoiler = false, updatedAtMillis = 100L))
            fake.ratingNow() shouldBe 4.5f

            fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Unreachable))
            fake.deleteMyReview(uuid) shouldBe ReviewWriteResult.Failed(ReviewError.Unreachable)
            fake.ratingNow() shouldBe 4.5f

            fake.deleteMyReview(uuid) shouldBe ReviewWriteResult.Saved(null)
            (fake.getReviewsFlow(uuid).first() as ReviewsState.Loaded).reviews.mine shouldBe null

            fake.updateCalls shouldBe listOf(uuid to ReviewPatch(rating = Patch.Set(4.5f)))
            fake.deleteCalls shouldContainExactly listOf(uuid, uuid)
            fake.refreshCalls.shouldBeEmpty()
        }

        it("TC-34: seed and refreshResults keep their behaviour, and a save starts no refetch") {
            val fake = FakeReviewsRepository()
            fake.seed(uuid, loaded(4f))
            fake.refreshResults.addLast(loaded(3f))

            fake.refresh(uuid)
            fake.ratingNow() shouldBe 3f
            fake.refreshCalls shouldContainExactly listOf(uuid)

            fake.updateMyReview(uuid, ReviewPatch(rating = Patch.Set(5f)))
            fake.refreshCalls shouldContainExactly listOf(uuid)
        }
    }

    describe("the write rules") {

        it("TC-35: an older write's success does not replace a newer write's value, which then rolls back to the confirmed one") {
            coroutineScope {
                val fake = FakeReviewsRepository()
                fake.seed(uuid, loaded(4f))
                val gateA = CompletableDeferred<Unit>()
                val gateB = CompletableDeferred<Unit>()
                fake.writeGates.addLast(gateA)
                fake.writeGates.addLast(gateB)
                fake.writeResults.addLast(ReviewWriteResult.Saved(loaded(4.5f).reviews.mine))
                fake.writeResults.addLast(ReviewWriteResult.Failed(ReviewError.Http(500)))

                val a = async(start = CoroutineStart.UNDISPATCHED) { fake.updateMyReview(uuid, ReviewPatch(rating = Patch.Set(4.5f))) }
                val b = async(start = CoroutineStart.UNDISPATCHED) { fake.updateMyReview(uuid, ReviewPatch(rating = Patch.Set(3.5f))) }
                fake.ratingNow() shouldBe 3.5f

                gateA.complete(Unit)
                a.await() shouldBe ReviewWriteResult.Saved(loaded(4.5f).reviews.mine)
                fake.ratingNow() shouldBe 3.5f

                gateB.complete(Unit)
                // A's success was the last confirmed answer, so B's failure rolls back to it.
                b.await() shouldBe ReviewWriteResult.Failed(ReviewError.Http(500))
                fake.ratingNow() shouldBe 4.5f
            }
        }

        it("TC-35: a write on a state that is not Loaded fails with NotLoaded, and a blank uuid with BookNotFound") {
            val fake = FakeReviewsRepository()
            fake.updateMyReview(uuid, ReviewPatch(rating = Patch.Set(4f))) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)
            fake.deleteMyReview(uuid) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)

            fake.seed(uuid, ReviewsState.Failed)
            fake.updateMyReview(uuid, ReviewPatch()) shouldBe ReviewWriteResult.Failed(ReviewError.NotLoaded)

            fake.seed(uuid, loaded(4f))
            fake.updateMyReview(" ", ReviewPatch(rating = Patch.Set(4f))) shouldBe ReviewWriteResult.Failed(ReviewError.BookNotFound)
            fake.deleteMyReview("") shouldBe ReviewWriteResult.Failed(ReviewError.BookNotFound)
            fake.updateCalls.shouldBeEmpty()
            fake.deleteCalls.shouldBeEmpty()
        }

        it("TC-35: a patch that changes nothing returns Unchanged and records no call") {
            val fake = FakeReviewsRepository()
            fake.seed(uuid, loaded(4f))

            fake.updateMyReview(uuid, ReviewPatch(rating = Patch.Set(4f))) shouldBe ReviewWriteResult.Unchanged
            fake.updateMyReview(uuid, ReviewPatch()) shouldBe ReviewWriteResult.Unchanged

            fake.updateCalls.shouldBeEmpty()
        }
    }
})
