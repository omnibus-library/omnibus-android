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

/** A controller over a recording scroller, with the strip at 700 px and the pin at 100 px: its pin point is scroll 600. */
private class Harness(scope: CoroutineScope, maxScroll: Int) {
    val anchorTop: MutableState<Int?> = mutableStateOf(null)
    val scrolls = mutableListOf<Int>()
    val state = BookDetailTabState(
        selectedState = mutableStateOf(BookDetailTab.Overview),
        scope = scope,
        scroller = { scrolls += it },
        maxScroll = { maxScroll },
        anchorTopInHost = { anchorTop.value },
    ).apply {
        stripTopPx = 700
        pinTopPx = 100
        stripHeightPx = 50
        clearancePx = 16
    }
}

class BookDetailTabStateTest : DescribeSpec({

    fun sut(scope: CoroutineScope, maxScroll: Int = 5000) = Harness(scope, maxScroll)

    describe("selecting a tab") {

        context("when the tab is earlier in the strip") {
            it("#94 TC-21: enters backward") {
                runTest {
                    val h = sut(backgroundScope)
                    h.state.select(BookDetailTab.Files)
                    h.state.entryDirection shouldBe 1

                    h.state.select(BookDetailTab.Overview)

                    h.state.entryDirection shouldBe -1
                }
            }
        }

        context("when the tab is already selected") {
            it("#94 TC-22: keeps the entry direction") {
                runTest {
                    val h = sut(backgroundScope)
                    h.state.select(BookDetailTab.Reviews)
                    h.state.select(BookDetailTab.Overview)

                    h.state.select(BookDetailTab.Overview)

                    h.state.entryDirection shouldBe -1
                }
            }
        }
    }

    describe("jumping to a tab") {

        it("#94 TC-23: selects the tab with the strip at the pin point") {
            runTest {
                val h = sut(backgroundScope)

                h.state.jumpTo(BookDetailTab.Files)
                runCurrent()

                h.state.selected shouldBe BookDetailTab.Files
                h.scrolls shouldContainExactly listOf(600)
            }
        }
    }

    describe("scrolling to an anchor") {

        context("when the anchor is laid out") {
            it("#94 TC-25: lands it below the pinned strip") {
                runTest {
                    val h = sut(backgroundScope)
                    h.anchorTop.value = 900

                    h.state.scrollTo(BookDetailAnchor.Series)
                    runCurrent()

                    h.scrolls shouldContainExactly listOf(1434)
                }
            }
        }

        context("when another tab is selected") {
            context("when the anchor's panel is still entering") {
                it("#94 TC-26: holds the scroll") {
                    runTest {
                        val h = sut(backgroundScope)
                        h.state.select(BookDetailTab.Files)
                        h.state.scrollTo(BookDetailAnchor.Series)

                        h.anchorTop.value = 900
                        Snapshot.sendApplyNotifications()
                        advanceTimeBy(100)
                        runCurrent()

                        h.scrolls.shouldBeEmpty()
                    }
                }
            }

            context("when the anchor's panel has settled") {
                it("#94 TC-26: scrolls") {
                    runTest {
                        val h = sut(backgroundScope)
                        h.state.select(BookDetailTab.Files)
                        h.state.scrollTo(BookDetailAnchor.Series)
                        h.anchorTop.value = 900

                        h.state.settledTab = BookDetailTab.Overview
                        Snapshot.sendApplyNotifications()
                        advanceTimeBy(100)
                        runCurrent()

                        h.scrolls shouldContainExactly listOf(1434)
                    }
                }
            }
        }

        context("when the anchor never lays out") {
            it("#94 TC-27: falls back to the pin point") {
                runTest {
                    val h = sut(backgroundScope)

                    h.state.scrollTo(BookDetailAnchor.Series)
                    advanceTimeBy(2000)
                    runCurrent()

                    h.scrolls shouldContainExactly listOf(600)
                }
            }
        }

        context("when a jump follows it") {
            it("#94 TC-29: drops the pending scroll") {
                runTest {
                    val h = sut(backgroundScope)
                    h.state.scrollTo(BookDetailAnchor.Series)
                    runCurrent()

                    h.state.jumpTo(BookDetailTab.Files)
                    advanceTimeBy(2000)
                    runCurrent()

                    h.scrolls shouldContainExactly listOf(600)
                }
            }
        }

        context("when a tap follows it") {
            it("#94 TC-29: drops the pending scroll") {
                runTest {
                    val h = sut(backgroundScope)
                    h.state.scrollTo(BookDetailAnchor.Series)
                    runCurrent()

                    h.state.select(BookDetailTab.Reviews)
                    advanceTimeBy(2000)
                    runCurrent()

                    h.scrolls.shouldBeEmpty()
                }
            }
        }
    }

    describe("unregistering an anchor") {

        context("when a newer registration has replaced it") {
            it("#94 TC-28: keeps the newer one") {
                runTest {
                    val state = sut(backgroundScope).state
                    val older = mockk<LayoutCoordinates>(relaxed = true)
                    val newer = mockk<LayoutCoordinates>(relaxed = true)
                    state.register(BookDetailAnchor.Series, older)
                    state.register(BookDetailAnchor.Series, newer)

                    state.unregister(BookDetailAnchor.Series, older)

                    state.registeredCoordinates(BookDetailAnchor.Series) shouldBe newer
                }
            }
        }
    }
})
