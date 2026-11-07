package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.compose.runtime.saveable.SaverScope
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BookCoverTransitionStateTest : DescribeSpec({

    fun sut() = BookCoverTransitionState()

    describe("the key a book's cover uses") {

        context("when no open has been recorded") {
            it("#122 TC-2: comes from the grid") {
                val uuid = randomString()

                sut().keyFor(uuid) shouldBe BookCoverKey(uuid, BookCoverOrigin.Grid)
            }
        }

        context("when the book has been opened twice") {
            it("#122 TC-3: comes from the latest tap") {
                val uuid = randomString()
                val state = sut()
                state.opened(uuid, BookCoverOrigin.Grid)

                state.opened(uuid, BookCoverOrigin.Continue)

                state.keyFor(uuid) shouldBe BookCoverKey(uuid, BookCoverOrigin.Continue)
            }
        }
    }

    describe("saving the recorded opens") {

        it("#122 TC-5: restores them, even with colons in the uuid") {
            val state = sut()
            state.opened("uuid:with:colons", BookCoverOrigin.Continue)

            val saved = with(BookCoverTransitionState.Saver) { SaverScope { true }.save(state) }!!
            val restored = BookCoverTransitionState.Saver.restore(saved)!!

            restored.keyFor("uuid:with:colons") shouldBe BookCoverKey("uuid:with:colons", BookCoverOrigin.Continue)
        }

        context("when a saved entry is malformed") {
            it("#122 TC-5: ignores it, keeping the good ones") {
                val restored = BookCoverTransitionState.fromSaveable(
                    listOf("no-separator", "Nowhere:uuid-4", "Continue:", "Continue:uuid-5"),
                )

                restored.keyFor("uuid-4") shouldBe BookCoverKey("uuid-4", BookCoverOrigin.Grid)
                restored.keyFor("uuid-5") shouldBe BookCoverKey("uuid-5", BookCoverOrigin.Continue)
            }
        }
    }

    describe("whether a cover may join the transition") {

        val window = BookCoverTransitionState.JOIN_WINDOW_NANOS

        context("when the open has no frame yet") {
            it("#122 TC-26: may join") {
                val uuid = randomString()
                val state = sut()
                state.opened(uuid, BookCoverOrigin.Grid)

                state.allowsJoin(uuid, randomLong()) shouldBe true
            }
        }

        context("when the open's frame has started") {
            it("#122 TC-26: may join up to the window's edge") {
                val uuid = randomString()
                val frame = randomLong()
                val state = sut()
                state.opened(uuid, BookCoverOrigin.Grid)
                state.openFrameStarted(uuid, frame)

                state.allowsJoin(uuid, frame + window) shouldBe true
                state.allowsJoin(uuid, frame + window + 1) shouldBe false
            }

            context("when the book is opened again") {
                it("#122 TC-26: forgets the old frame") {
                    val uuid = randomString()
                    val frame = randomLong()
                    val state = sut()
                    state.opened(uuid, BookCoverOrigin.Grid)
                    state.openFrameStarted(uuid, frame)

                    state.opened(uuid, BookCoverOrigin.Grid)

                    state.allowsJoin(uuid, frame + window + 1) shouldBe true
                }
            }
        }
    }
})
