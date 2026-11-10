package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.ui.EdgeFadeLength
import com.omnibus.omnibus.feature.bookdetail.ui.EdgeFadeReach
import com.omnibus.omnibus.feature.bookdetail.ui.FadeBand
import com.omnibus.omnibus.feature.bookdetail.ui.StageActionRowHeight
import com.omnibus.omnibus.feature.bookdetail.ui.StageRestTop
import com.omnibus.omnibus.feature.bookdetail.ui.TopBarRowHeight
import com.omnibus.omnibus.feature.bookdetail.ui.bookDetailPaneSplit
import com.omnibus.omnibus.feature.bookdetail.ui.columnContentOffsetPx
import com.omnibus.omnibus.feature.bookdetail.ui.columnScrollForContentOffset
import com.omnibus.omnibus.feature.bookdetail.ui.splitAtShare
import com.omnibus.omnibus.feature.bookdetail.ui.stageBottomFadeBand
import com.omnibus.omnibus.feature.bookdetail.ui.stageContentTop
import com.omnibus.omnibus.feature.bookdetail.ui.stageCoverScale
import com.omnibus.omnibus.feature.bookdetail.ui.stageTopFadeBand
import com.omnibus.omnibus.feature.bookdetail.ui.stageUnderBar
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class BookDetailLayoutTest : DescribeSpec({

    describe("splitting the book detail page") {

        context("when the window is narrower than the minimum width") {
            it("#211 TC-3: stays one column, landscape or not") {
                val width = randomInt(300..559)
                val short = randomInt(100..width - 1)
                val tall = width + randomInt(1..800)

                bookDetailPaneSplit(width.dp, short.dp).shouldBeNull()
                bookDetailPaneSplit(width.dp, tall.dp).shouldBeNull()
            }

            it("#211 TC-8: stays one column just under the minimum") {
                bookDetailPaneSplit(559.99f.dp, 300.dp).shouldBeNull()
            }
        }

        context("when the window is not wider than it is tall") {
            it("#211 TC-5: stays one column at any width at or above the minimum") {
                val width = randomInt(560..1000)
                val tall = width + randomInt(1..800)

                bookDetailPaneSplit(width.dp, tall.dp).shouldBeNull()
                bookDetailPaneSplit(width.dp, width.dp).shouldBeNull()
            }
        }

        context("when the window is landscape and at least the minimum width") {
            it("#211 TC-2: splits the width into the two panes") {
                val width = randomInt(560..1000)
                val height = randomInt(200..559)

                val split = bookDetailPaneSplit(width.dp, height.dp).shouldNotBeNull()

                split.leading + split.trailing shouldBe width.dp
            }

            it("#211 TC-7: splits at exactly the minimum width") {
                bookDetailPaneSplit(560.dp, randomInt(100..559).dp).shouldNotBeNull()
            }

            it("#211 TC-10: splits when it is one dp wider than tall") {
                val width = randomInt(560..1400)

                bookDetailPaneSplit(width.dp, (width - 1).dp).shouldNotBeNull()
            }
        }

        context("when the leading share is computed for any width") {
            it("#211 TC-11: rounds the leading pane to a whole dp, half up") {
                checkAll(Arb.int(560..1400)) { width ->
                    val leading = bookDetailPaneSplit(width.dp, (width - 1).dp).shouldNotBeNull().leading.value

                    leading shouldBe ((width * 42 + 50) / 100).toFloat()
                }
            }

            it("#211 TC-12: rounds 493.5 up to 494 dp for a width of 1175 dp") {
                val split = bookDetailPaneSplit(1175.dp, 800.dp).shouldNotBeNull()

                split.leading shouldBe 494.dp
                split.trailing shouldBe 681.dp
            }
        }

        context("when two panes are forced") {
            it("#211 TC-13: splits any width at 42%") {
                val width = randomInt(300..1400)

                splitAtShare(width.dp).leading shouldBe ((width * 42 + 50) / 100).dp
            }
        }
    }

    describe("deciding whether the stage rests under the bar") {

        context("when the leading pane is at least 300 dp") {
            it("#211 TC-14: rests the stage under the bar") {
                stageUnderBar(300.dp) shouldBe true
                stageUnderBar(randomInt(301..2000).dp) shouldBe true
            }
        }

        context("when the leading pane is narrower than 300 dp") {
            it("#211 TC-14: does not rest the stage under the bar") {
                stageUnderBar(299.99f.dp) shouldBe false
                stageUnderBar(randomDouble(0.0, 299.99).toFloat().dp) shouldBe false
            }
        }
    }

    describe("scaling the stage cover") {

        context("when the pane is tall") {
            it("#211 TC-15: is exactly 1 once the formula exceeds 1") {
                val height = randomInt(1000..2000).dp

                stageCoverScale(height, underBar = true) shouldBe 1f
                stageCoverScale(height, underBar = false) shouldBe 1f
            }
        }

        context("when the pane is at or below the action row") {
            it("#211 TC-16: is exactly 0.5 under the bar and not under it") {
                checkAll(Exhaustive.of(true, false)) { underBar ->
                    stageCoverScale(StageActionRowHeight, underBar) shouldBe 0.5f
                }
            }
        }

        context("when the pane is of middle height") {
            it("#211 TC-17: equals the free height times 0.44 over the cover height") {
                checkAll(Exhaustive.of(true, false)) { underBar ->
                    val height = randomDouble(400.0, 630.0).toFloat()
                    val free = height - 62f - (if (underBar) 0f else TopBarRowHeight.value)
                    val expected = free * 0.44f / 252f

                    val actual = stageCoverScale(height.dp, underBar)

                    abs(actual - expected) shouldBeLessThan 1e-5f
                }
            }

            it("#211 TC-18: scales smaller when the bar row is deducted") {
                val height = randomDouble(400.0, 630.0).toFloat().dp

                stageCoverScale(height, underBar = false) shouldBeLessThan stageCoverScale(height, underBar = true)
            }
        }

        context("for any pane height and either underBar value") {
            it("#211 TC-19: stays within 0.5 to 1 and never shrinks as the pane grows") {
                // Whole dp, since Arb.float also emits NaN, which is no pane height.
                checkAll(Arb.int(0..2000), Arb.int(0..2000), Arb.boolean()) { a, b, underBar ->
                    val low = stageCoverScale(min(a, b).dp, underBar)
                    val high = stageCoverScale(max(a, b).dp, underBar)

                    (low in 0.5f..1f) shouldBe true
                    (high in 0.5f..1f) shouldBe true
                    low shouldBeLessThanOrEqualTo high
                }
            }
        }
    }

    describe("handing the content offset between layouts") {

        context("when the column scroll is above, at, or below the pin point") {
            it("#211 TC-20: reports 0 above and at the pin point, and tracks the scroll below it") {
                val pinTop = randomInt(0..50)
                val stripTop = randomInt(100..1000) + pinTop
                val base = stripTop - pinTop

                columnContentOffsetPx(base - 1, stripTop, pinTop) shouldBe 0
                columnContentOffsetPx(base, stripTop, pinTop) shouldBe 0
                columnContentOffsetPx(base + 10, stripTop, pinTop) shouldBe 10
            }
        }

        context("when there is no offset") {
            it("#211 TC-21: restores the page to the top, whatever the strip position") {
                val stripTop = randomInt(-1..2000)
                val pinTop = randomInt(0..200)

                columnScrollForContentOffset(0, stripTop, pinTop, randomInt(0..5000)) shouldBe 0
            }
        }

        context("when the offset is positive") {
            it("#211 TC-22: lands the offset under the strip, clamped to the scroll range") {
                val stripTop = randomInt(50..1000)
                val pinTop = randomInt(0..50)
                val offset = randomInt(1..500)
                val target = stripTop - pinTop + offset

                columnScrollForContentOffset(offset, stripTop, pinTop, target + 100) shouldBe target
                columnScrollForContentOffset(offset, stripTop, pinTop, target - 1) shouldBe target - 1
            }
        }

        context("when the offset is carried through a column scroll") {
            it("#211 TC-22: round trips the offset with TC-20 for offsets within the range") {
                checkAll(Arb.int(1..2000)) { offset ->
                    // The strip sits below the pin point, as it does once the page is laid out.
                    val stripTop = randomInt(50..1000)
                    val pinTop = randomInt(0..50)

                    val scroll = columnScrollForContentOffset(offset, stripTop, pinTop, 1_000_000)

                    columnContentOffsetPx(scroll, stripTop, pinTop) shouldBe offset
                }
            }
        }
    }

    describe("fading the stage pane under the bar") {

        context("when the stage rests under the bar") {
            it("#211 TC-73: clears 24 dp above the rest line and is opaque at it") {
                val statusTop = randomDouble(0.0, 80.0).toFloat().dp

                stageTopFadeBand(statusTop, underBar = true) shouldBe
                    FadeBand(statusTop + 10.dp - 24.dp, statusTop + 10.dp)
            }
        }

        context("when the stage starts below the bar row") {
            it("#211 TC-73: clears 4 dp above the bar row's bottom and is opaque 24 dp lower") {
                val statusTop = randomDouble(0.0, 80.0).toFloat().dp
                val clear = statusTop + TopBarRowHeight - 4.dp

                stageTopFadeBand(statusTop, underBar = false) shouldBe FadeBand(clear, clear + 24.dp)
            }
        }
    }

    describe("fading the stage pane above the action row") {

        context("when the pane has an action row") {
            it("#211 TC-74: is clear 4 dp above the action row's top and opaque 24 dp higher") {
                val paneHeight = randomInt(300..900).dp
                val actionArea = randomInt(50..80).dp
                val clear = paneHeight - actionArea + 4.dp

                stageBottomFadeBand(paneHeight, actionArea) shouldBe FadeBand(clear, clear - 24.dp)
            }
        }
    }

    describe("placing the stage content in the leading pane") {

        // The room each fade needs between the content and the bar row or the action row.
        val fadeClearance = EdgeFadeLength - EdgeFadeReach

        context("when the content fits between the bar row and the action row clear of the fades") {
            it("#216: centres the content between the bar row's bottom and the action row's top") {
                val statusTop = randomInt(0..80).dp
                val paneHeight = randomInt(500..1200).dp
                val actionArea = randomInt(0..80).dp
                val bandTop = statusTop + TopBarRowHeight
                val bandBottom = paneHeight - actionArea
                val content = randomInt(0..(bandBottom - bandTop - fadeClearance * 2).value.toInt()).dp

                val top = stageContentTop(statusTop, randomBoolean(), paneHeight, actionArea, content)

                top - bandTop shouldBe bandBottom - (top + content)
            }

            it("#216: keeps the content's bottom inside the opaque part of the bottom fade, under the bar or not") {
                checkAll(Arb.int(0..80), Arb.int(500..1200), Arb.int(0..80), Arb.boolean()) { status, pane, action, underBar ->
                    val statusTop = status.dp
                    val paneHeight = pane.dp
                    val actionArea = action.dp
                    val content = paneHeight - actionArea - statusTop - TopBarRowHeight - fadeClearance * 2

                    val top = stageContentTop(statusTop, underBar, paneHeight, actionArea, content)

                    top + content shouldBeLessThanOrEqualTo stageBottomFadeBand(paneHeight, actionArea).opaque
                }
            }

            context("when the stage starts below the bar row") {
                it("#216: keeps the content's top inside the opaque part of the top fade") {
                    val statusTop = randomInt(0..80).dp
                    val paneHeight = randomInt(500..1200).dp
                    val actionArea = randomInt(0..80).dp
                    val content = paneHeight - actionArea - statusTop - TopBarRowHeight - fadeClearance * 2

                    val top = stageContentTop(statusTop, underBar = false, paneHeight, actionArea, content)

                    top shouldBeGreaterThanOrEqualTo stageTopFadeBand(statusTop, underBar = false).opaque
                }
            }
        }

        context("when the content does not fit between the bar row and the action row clear of the fades") {

            context("when the stage rests under the bar") {
                it("#216: puts the content at the rest line below the status bar, however far the content overflows") {
                    val statusTop = randomInt(0..80).dp
                    val paneHeight = randomInt(300..1200).dp
                    val actionArea = randomInt(0..80).dp
                    // 1 dp short of the room the fades need, the tightest content that does not fit.
                    val tightest = paneHeight - actionArea - statusTop - TopBarRowHeight - fadeClearance * 2 + 1.dp

                    checkAll(Arb.int(0..600)) { overflow ->
                        stageContentTop(statusTop, underBar = true, paneHeight, actionArea, tightest + overflow.dp) shouldBe
                            statusTop + StageRestTop
                    }
                }
            }

            context("when the stage starts below the bar row") {
                it("#216: puts the content at the rest line below the bar row, however far the content overflows") {
                    val statusTop = randomInt(0..80).dp
                    val paneHeight = randomInt(300..1200).dp
                    val actionArea = randomInt(0..80).dp
                    // 1 dp short of the room the fades need, the tightest content that does not fit.
                    val tightest = paneHeight - actionArea - statusTop - TopBarRowHeight - fadeClearance * 2 + 1.dp

                    checkAll(Arb.int(0..600)) { overflow ->
                        stageContentTop(statusTop, underBar = false, paneHeight, actionArea, tightest + overflow.dp) shouldBe
                            statusTop + TopBarRowHeight + StageRestTop
                    }
                }
            }
        }
    }
})
