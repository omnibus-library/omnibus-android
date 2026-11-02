package com.omnibus.omnibus.feature.bookdetail.tabs

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.LayoutCoordinates
import com.omnibus.omnibus.feature.bookdetail.BookDetailAnchor
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

/** A controller over a recording scroller, with the strip at 700 px and the pin at 100 px (max scroll 5000 unless set). */
private class Harness(
    scope: CoroutineScope,
    maxScroll: Int = 5000,
    val fakeTop: MutableState<Int?> = mutableStateOf<Int?>(null),
) {
    val selectedState: MutableState<BookDetailTab> = mutableStateOf(BookDetailTab.Overview)
    val calls = mutableListOf<Int>()
    val state = BookDetailTabState(
        selectedState = selectedState,
        scope = scope,
        scroller = { calls += it },
        maxScroll = { maxScroll },
        anchorTopInHost = { fakeTop.value },
    ).apply {
        stripTopPx = 700
        pinTopPx = 100
        stripHeightPx = 50
        clearancePx = 16
    }
}

class BookDetailTabStateTest : DescribeSpec({

    describe("BookDetailTabState") {

        it("#94 TC-20: opens on Overview") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.selected shouldBe BookDetailTab.Overview
                h.state.entryDirection shouldBe 0
                h.state.settledTab shouldBe BookDetailTab.Overview
                h.calls.shouldBeEmpty()
            }
        }

        it("#94 TC-21: select changes the tab and records the entry direction") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.select(BookDetailTab.Reviews)
                h.state.selected shouldBe BookDetailTab.Reviews
                h.state.entryDirection shouldBe 1
                h.selectedState.value shouldBe BookDetailTab.Reviews

                h.state.select(BookDetailTab.Files)
                h.state.selected shouldBe BookDetailTab.Files
                h.state.entryDirection shouldBe 1

                h.state.select(BookDetailTab.Overview)
                h.state.selected shouldBe BookDetailTab.Overview
                h.state.entryDirection shouldBe -1
                h.selectedState.value shouldBe BookDetailTab.Overview
                h.calls.shouldBeEmpty()
            }
        }

        it("#94 TC-22: selecting the selected tab does nothing") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.select(BookDetailTab.Reviews)
                h.state.select(BookDetailTab.Reviews)
                h.state.selected shouldBe BookDetailTab.Reviews
                h.state.entryDirection shouldBe 1
                h.calls.shouldBeEmpty()
            }
        }

        it("#94 TC-23: jumpTo selects the tab and scrolls the strip to the pin point") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.jumpTo(BookDetailTab.Files)
                runCurrent()
                h.state.selected shouldBe BookDetailTab.Files
                h.calls shouldContainExactly listOf(600)
            }
        }

        it("#94 TC-24: jumpTo scrolls up or down to the same target and clamps") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.jumpTo(BookDetailTab.Files)
                runCurrent()
                h.calls shouldContainExactly listOf(600)

                val clamped = Harness(backgroundScope, maxScroll = 400)
                clamped.state.jumpTo(BookDetailTab.Files)
                runCurrent()
                clamped.calls shouldContainExactly listOf(400)
            }
        }

        it("#94 TC-25: scrollTo lands the anchor below the pinned strip") {
            runTest {
                val h = Harness(backgroundScope)
                h.fakeTop.value = 900
                h.state.scrollTo(BookDetailAnchor.Series)
                runCurrent()
                h.state.selected shouldBe BookDetailTab.Overview
                h.calls shouldContainExactly listOf(1434)
            }
        }

        it("#94 TC-26: scrollTo waits for the anchor and the settled panel") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.select(BookDetailTab.Files)
                h.state.scrollTo(BookDetailAnchor.Series)
                h.state.selected shouldBe BookDetailTab.Overview
                advanceTimeBy(300)
                runCurrent()
                h.calls.shouldBeEmpty()

                // The anchor is laid out but the panel is still entering: nothing scrolls yet.
                h.fakeTop.value = 900
                Snapshot.sendApplyNotifications()
                advanceTimeBy(100)
                runCurrent()
                h.calls.shouldBeEmpty()

                h.state.settledTab = BookDetailTab.Overview
                Snapshot.sendApplyNotifications()
                advanceTimeBy(100)
                runCurrent()
                h.calls shouldContainExactly listOf(1434)
            }
        }

        it("#94 TC-27: scrollTo falls back to jumpTo after one second") {
            runTest {
                val h = Harness(backgroundScope)
                h.state.scrollTo(BookDetailAnchor.Series)
                advanceTimeBy(999)
                runCurrent()
                h.calls.shouldBeEmpty()

                advanceTimeBy(2)
                runCurrent()
                h.calls shouldContainExactly listOf(jumpScrollTarget(700, 100, 5000))
                h.state.selected shouldBe BookDetailTab.Overview
            }
        }

        it("#94 TC-28: unregister only removes the matching registration") {
            runTest {
                val state = BookDetailTabState(
                    selectedState = mutableStateOf(BookDetailTab.Overview),
                    scope = backgroundScope,
                    scroller = { },
                    maxScroll = { 0 },
                )
                val a = mockk<LayoutCoordinates>(relaxed = true)
                val b = mockk<LayoutCoordinates>(relaxed = true)
                state.register(BookDetailAnchor.Series, a)
                state.register(BookDetailAnchor.Series, b)
                state.unregister(BookDetailAnchor.Series, a)
                state.registeredCoordinates(BookDetailAnchor.Series) shouldBe b
                state.unregister(BookDetailAnchor.Series, b)
                state.registeredCoordinates(BookDetailAnchor.Series) shouldBe null
            }
        }

        it("#94 TC-29: a newer jump or a tap supersedes a pending scrollTo") {
            runTest {
                val jumped = Harness(backgroundScope)
                jumped.state.scrollTo(BookDetailAnchor.Series)
                runCurrent()
                jumped.state.jumpTo(BookDetailTab.Files)
                advanceTimeBy(2000)
                runCurrent()
                jumped.calls shouldContainExactly listOf(600)
                jumped.state.selected shouldBe BookDetailTab.Files

                val tapped = Harness(backgroundScope)
                tapped.state.scrollTo(BookDetailAnchor.Series)
                runCurrent()
                tapped.state.select(BookDetailTab.Reviews)
                advanceTimeBy(2000)
                runCurrent()
                tapped.calls.shouldBeEmpty()
                tapped.state.selected shouldBe BookDetailTab.Reviews
            }
        }
    }
})
