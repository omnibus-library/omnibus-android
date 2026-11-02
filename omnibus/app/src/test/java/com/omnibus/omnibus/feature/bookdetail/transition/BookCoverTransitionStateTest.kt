package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.compose.runtime.saveable.SaverScope
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class BookCoverTransitionStateTest : DescribeSpec({

    describe("BookCoverTransitionState") {

        it("issue122_tc2_keyForDefaultsToGridWithNoRecord") {
            val state = BookCoverTransitionState()

            state.keyFor("uuid-1") shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Grid)
        }

        it("issue122_tc3_openedRecordsOriginAndLatestTapWins") {
            val state = BookCoverTransitionState()

            state.opened("uuid-1", BookCoverOrigin.Continue)
            state.keyFor("uuid-1") shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Continue)

            state.opened("uuid-1", BookCoverOrigin.Grid)
            state.keyFor("uuid-1") shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Grid)
        }

        it("issue122_tc4_originsAreKeptPerBook") {
            val state = BookCoverTransitionState()

            state.opened("uuid-1", BookCoverOrigin.Continue)
            state.opened("uuid-2", BookCoverOrigin.Grid)

            state.keyFor("uuid-1") shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Continue)
            state.keyFor("uuid-2") shouldBe BookCoverKey("uuid-2", BookCoverOrigin.Grid)
            state.keyFor("uuid-3") shouldBe BookCoverKey("uuid-3", BookCoverOrigin.Grid)
        }

        it("issue122_tc5_saverRoundTripsRecordedOrigins") {
            val state = BookCoverTransitionState()
            state.opened("uuid-1", BookCoverOrigin.Continue)
            state.opened("uuid:with:colons", BookCoverOrigin.Grid)

            val saved = with(BookCoverTransitionState.Saver) { SaverScope { true }.save(state) }!!
            val restored = BookCoverTransitionState.Saver.restore(saved)!!

            restored.keyFor("uuid-1") shouldBe BookCoverKey("uuid-1", BookCoverOrigin.Continue)
            restored.keyFor("uuid:with:colons") shouldBe BookCoverKey("uuid:with:colons", BookCoverOrigin.Grid)
            restored.keyFor("uuid-9") shouldBe BookCoverKey("uuid-9", BookCoverOrigin.Grid)

            // Empty restores to empty, and a malformed entry is ignored without throwing.
            BookCoverTransitionState.fromSaveable(emptyList()).keyFor("uuid-1") shouldBe
                BookCoverKey("uuid-1", BookCoverOrigin.Grid)
            val malformed = BookCoverTransitionState.fromSaveable(
                listOf("no-separator", "Nowhere:uuid-4", "Continue:", "Continue:uuid-5"),
            )
            malformed.keyFor("uuid-4") shouldBe BookCoverKey("uuid-4", BookCoverOrigin.Grid)
            malformed.keyFor("uuid-5") shouldBe BookCoverKey("uuid-5", BookCoverOrigin.Continue)
        }

        it("issue122_tc26_allowsJoinOnlyInsideTheWindow") {
            val window = BookCoverTransitionState.JOIN_WINDOW_NANOS
            val frame = 5_000_000_000L

            // No pending open: a cover composed without a tap may join.
            BookCoverTransitionState().allowsJoin("uuid-1", frame) shouldBe true

            // Pending open with no frame yet: may join.
            val state = BookCoverTransitionState()
            state.opened("uuid-1", BookCoverOrigin.Grid)
            state.allowsJoin("uuid-1", frame) shouldBe true

            // Frame recorded: join at the window edge, not after it.
            state.openFrameStarted("uuid-1", frame)
            state.allowsJoin("uuid-1", frame + window) shouldBe true
            state.allowsJoin("uuid-1", frame + window + 1) shouldBe false

            // A different book's pending open does not block this book.
            state.allowsJoin("uuid-2", frame + window + 1) shouldBe true

            // Opening again resets the frame, so the old frame no longer counts.
            state.opened("uuid-1", BookCoverOrigin.Grid)
            state.allowsJoin("uuid-1", frame + window + 1) shouldBe true
            state.openFrameStarted("uuid-1", frame + window + 1)
            state.allowsJoin("uuid-1", frame + window + 1 + window + 1) shouldBe false
        }
    }
})
