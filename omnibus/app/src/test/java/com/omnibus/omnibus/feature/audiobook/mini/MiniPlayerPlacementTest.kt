package com.omnibus.omnibus.feature.audiobook.mini

import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.testing.TEST_SEED
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class MiniPlayerPlacementTest : DescribeSpec({

    describe("hiding the bar in the shell") {
        // P-MINI-1
        it("#172 TC-13: hides it under Book Detail") {
            shellHidesMiniPlayer(BookDetailRoute(randomString())) shouldBe true
        }

        // P-MINI-1
        it("#172 TC-14: shows it over every tab root, the Book Detail placeholder or an empty stack") {
            val tops: List<NavKey?> = ShellTab.entries.map { it.root } + BookDetailPlaceholderRoute(randomString()) + null

            checkAll(Exhaustive.collection(tops)) { top ->
                shellHidesMiniPlayer(top) shouldBe false
            }
        }
    }

    describe("collapsing the strip on scroll") {
        context("when the strip is expanded") {
            // #286 TC-1 (R4)
            it("#286 TC-1: collapses only once the downward scroll reaches the threshold") {
                checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(1f, 200f, includeNaNs = false)) { threshold ->
                    val underThreshold = MiniPlayerCollapse()
                        .afterScroll(-threshold / 2, threshold)
                        .afterScroll(-(threshold / 2 - 0.5f), threshold)

                    underThreshold.collapsed shouldBe false
                    underThreshold.afterScroll(-1f, threshold).collapsed shouldBe true
                }
            }

            // #286 TC-2 (R4)
            it("#286 TC-2: never collapses on alternating scroll deltas that each stay under the threshold") {
                checkAll(
                    PropTestConfig(seed = TEST_SEED),
                    Arb.float(1f, 200f, includeNaNs = false),
                    Arb.list(Arb.float(0.5f, 0.99f, includeNaNs = false), 3..40),
                ) { threshold, magnitudes ->
                    // Each step is under the threshold; three or more of them alternate past it in absolute sum.
                    val deltas = magnitudes.mapIndexed { index, magnitude ->
                        if (index % 2 == 0) -magnitude * threshold else magnitude * threshold
                    }

                    deltas.runningFold(MiniPlayerCollapse()) { state, dy -> state.afterScroll(dy, threshold) }
                        .map { it.collapsed }
                        .distinct() shouldBe listOf(false)
                }
            }
        }

        context("when the strip is collapsed") {
            // #286 TC-1 (R4)
            it("#286 TC-1: expands only once the upward scroll reaches the threshold") {
                checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(1f, 200f, includeNaNs = false)) { threshold ->
                    val underThreshold = MiniPlayerCollapse(collapsed = true)
                        .afterScroll(threshold / 2, threshold)
                        .afterScroll(threshold / 2 - 0.5f, threshold)

                    underThreshold.collapsed shouldBe true
                    underThreshold.afterScroll(1f, threshold).collapsed shouldBe false
                }
            }

            // #286 TC-2 (R4)
            it("#286 TC-2: never expands on alternating scroll deltas that each stay under the threshold") {
                checkAll(
                    PropTestConfig(seed = TEST_SEED),
                    Arb.float(1f, 200f, includeNaNs = false),
                    Arb.list(Arb.float(0.5f, 0.99f, includeNaNs = false), 3..40),
                ) { threshold, magnitudes ->
                    // Each step is under the threshold; three or more of them alternate past it in absolute sum.
                    val deltas = magnitudes.mapIndexed { index, magnitude ->
                        if (index % 2 == 0) -magnitude * threshold else magnitude * threshold
                    }

                    deltas.runningFold(MiniPlayerCollapse(collapsed = true)) { state, dy -> state.afterScroll(dy, threshold) }
                        .map { it.collapsed }
                        .distinct() shouldBe listOf(true)
                }
            }
        }

        context("when a scroll is part way to flipping the strip") {
            context("from expanded") {
                // #286 TC-3 (R4)
                it("#286 TC-3: ignores zero consumed scroll, whatever the count") {
                    checkAll(
                        PropTestConfig(seed = TEST_SEED),
                        Arb.float(1f, 200f, includeNaNs = false),
                        Arb.int(1..50),
                    ) { threshold, count ->
                        val start = MiniPlayerCollapse(collapsed = false, travel = -threshold / 2)

                        List(count) { 0f }.fold(start) { state, dy -> state.afterScroll(dy, threshold) } shouldBe start
                    }
                }
            }

            context("from collapsed") {
                // #286 TC-3 (R4)
                it("#286 TC-3: ignores zero consumed scroll, whatever the count") {
                    checkAll(
                        PropTestConfig(seed = TEST_SEED),
                        Arb.float(1f, 200f, includeNaNs = false),
                        Arb.int(1..50),
                    ) { threshold, count ->
                        val start = MiniPlayerCollapse(collapsed = true, travel = threshold / 2)

                        List(count) { 0f }.fold(start) { state, dy -> state.afterScroll(dy, threshold) } shouldBe start
                    }
                }
            }
        }
    }

    describe("choosing the rail block's variant") {
        // #286 TC-4 (R11)
        it("#286 TC-4: is full when the full block exactly fills the space") {
            checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(0f, 2000f, includeNaNs = false)) { height ->
                railMiniPlayerVariant(height.dp, height.dp) shouldBe RailMiniPlayerVariant.Full
            }
        }

        // #286 TC-4 (R11)
        it("#286 TC-4: is full when the full block is 1px (0.01dp) under the space") {
            checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(0.01f, 2000f, includeNaNs = false)) { height ->
                railMiniPlayerVariant(height.dp, height.dp - 0.01.dp) shouldBe RailMiniPlayerVariant.Full
            }
        }

        // #286 TC-4 (R11)
        it("#286 TC-4: is compact when the full block is 1px (0.01dp) over the space") {
            checkAll(PropTestConfig(seed = TEST_SEED), Arb.float(0f, 2000f, includeNaNs = false)) { height ->
                railMiniPlayerVariant(height.dp, height.dp + 0.01.dp) shouldBe RailMiniPlayerVariant.Compact
            }
        }

        // #286 TC-4 (R11)
        it("#286 TC-4: is full exactly when the full block fits the space, for any pair of heights") {
            checkAll(
                PropTestConfig(seed = TEST_SEED),
                Arb.float(0f, 2000f, includeNaNs = false),
                Arb.float(0f, 2000f, includeNaNs = false),
            ) { space, full ->
                (railMiniPlayerVariant(space.dp, full.dp) == RailMiniPlayerVariant.Full) shouldBe (full <= space)
            }
        }
    }
})
