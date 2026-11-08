package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import kotlin.math.floor

class ReaderRibbonRulesTest : DescribeSpec({

    describe("the ribbon's motion") {
        context("when the first frame is seen") {
            it("#224 TC-1: fades in when the page is bookmarked") {
                val frame = RibbonFrame(randomCfi(), shown = true, tapped = false)

                ribbonMotion(null, frame, reduceMotion = false) shouldBe RibbonMotion.FadeIn
            }

            it("#224 TC-1: does nothing when the page is not bookmarked") {
                val frame = RibbonFrame(randomCfi(), shown = false, tapped = false)

                ribbonMotion(null, frame, reduceMotion = false) shouldBe RibbonMotion.None
            }
        }

        context("when the same page flips with a tap") {
            it("#224 TC-1: unfurls when it becomes bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = false, tapped = false)
                val after = RibbonFrame(page, shown = true, tapped = true)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.Unfurl
            }

            it("#224 TC-1: furls when it stops being bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = true, tapped = false)
                val after = RibbonFrame(page, shown = false, tapped = true)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.Furl
            }
        }

        context("when the same page flips without a tap") {
            it("#224 TC-1: fades in when it becomes bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = false, tapped = false)
                val after = RibbonFrame(page, shown = true, tapped = false)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.FadeIn
            }

            it("#224 TC-1: fades out when it stops being bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = true, tapped = false)
                val after = RibbonFrame(page, shown = false, tapped = false)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.FadeOut
            }
        }

        context("when the page changes") {
            it("#224 TC-1: fades in to a bookmarked page") {
                val before = RibbonFrame(randomCfi(), shown = true, tapped = false)
                val after = RibbonFrame(randomCfi(), shown = true, tapped = false)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.FadeIn
            }

            it("#224 TC-1: drops to a page that is not bookmarked") {
                val before = RibbonFrame(randomCfi(), shown = true, tapped = false)
                val after = RibbonFrame(randomCfi(), shown = false, tapped = false)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.Drop
            }
        }

        context("when nothing the ribbon shows has changed") {
            it("#224 TC-1: is None for an identical frame") {
                val frame = RibbonFrame(randomCfi(), shown = true, tapped = true)

                ribbonMotion(frame, frame, reduceMotion = false) shouldBe RibbonMotion.None
            }

            it("#224 TC-1: is None when only the tap changed") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = true, tapped = false)
                val after = RibbonFrame(page, shown = true, tapped = true)

                ribbonMotion(before, after, reduceMotion = false) shouldBe RibbonMotion.None
            }
        }
    }

    describe("the ribbon's motion under reduced motion") {
        context("when the first frame is seen") {
            it("#224 TC-2: cross-fades in when the page is bookmarked") {
                val frame = RibbonFrame(randomCfi(), shown = true, tapped = false)

                ribbonMotion(null, frame, reduceMotion = true) shouldBe RibbonMotion.CrossfadeIn
            }
        }

        context("when the same page flips with a tap") {
            it("#224 TC-2: cross-fades in when it becomes bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = false, tapped = false)
                val after = RibbonFrame(page, shown = true, tapped = true)

                ribbonMotion(before, after, reduceMotion = true) shouldBe RibbonMotion.CrossfadeIn
            }

            it("#224 TC-2: cross-fades out when it stops being bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = true, tapped = false)
                val after = RibbonFrame(page, shown = false, tapped = true)

                ribbonMotion(before, after, reduceMotion = true) shouldBe RibbonMotion.CrossfadeOut
            }
        }

        context("when the same page flips without a tap") {
            it("#224 TC-2: cross-fades in when it becomes bookmarked") {
                val page = randomCfi()
                val before = RibbonFrame(page, shown = false, tapped = false)
                val after = RibbonFrame(page, shown = true, tapped = false)

                ribbonMotion(before, after, reduceMotion = true) shouldBe RibbonMotion.CrossfadeIn
            }
        }

        context("when the page changes") {
            it("#224 TC-2: cross-fades in to a bookmarked page") {
                val before = RibbonFrame(randomCfi(), shown = false, tapped = false)
                val after = RibbonFrame(randomCfi(), shown = true, tapped = false)

                ribbonMotion(before, after, reduceMotion = true) shouldBe RibbonMotion.CrossfadeIn
            }

            it("#224 TC-2: still drops to a page that is not bookmarked") {
                val before = RibbonFrame(randomCfi(), shown = true, tapped = false)
                val after = RibbonFrame(randomCfi(), shown = false, tapped = false)

                ribbonMotion(before, after, reduceMotion = true) shouldBe RibbonMotion.Drop
            }
        }
    }

    describe("the ribbon's placement on a single page") {
        context("with the spread off") {
            it("#224 TC-3: hangs 20 dp from the left inset, and is never two-page, for every margin") {
                checkAll(Exhaustive.enum<ReaderMargins>()) { margins ->
                    repeat(25) {
                        val width = randomInt(240..1600).toFloat()
                        val insetLeft = randomInt(0..48).toFloat()
                        val insetRight = randomInt(0..48).toFloat()

                        val placement = ribbonPlacement(width, insetLeft, insetRight, margins, spread = "none")

                        placement.startDp shouldBe insetLeft + 20f
                        placement.twoPage shouldBe false
                    }
                }
            }

            it("#224 TC-3: is translucent exactly when the text starts under the ribbon, for every margin") {
                checkAll(Exhaustive.enum<ReaderMargins>()) { margins ->
                    repeat(25) {
                        val width = randomInt(240..1600).toFloat()
                        val insetLeft = randomInt(0..48).toFloat()
                        val insetRight = randomInt(0..48).toFloat()

                        val placement = ribbonPlacement(width, insetLeft, insetRight, margins, spread = "none")

                        // The text's left edge, mirrored from the page's layout: the stage, the centred column, and
                        // half of the even gap that epub.js puts beside the text.
                        val stage = width - insetLeft - insetRight - 20f
                        val column = stage * margins.css.removeSuffix("%").toFloat() / 100f
                        val columnLeft = insetLeft + 10f + (stage - column) / 2f
                        val section = floor(column / 12f).toInt()
                        val textLeft = columnLeft + (if (section % 2 == 0) section else section - 1) / 2f

                        placement.translucent shouldBe (textLeft < placement.startDp + 28f)
                    }
                }
            }
        }

        context("with the spread off, at worked examples") {
            it("#224 TC-3: a 320 dp page is translucent under Narrow margins") {
                ribbonPlacement(320f, 0f, 0f, ReaderMargins.Narrow, spread = "none").translucent shouldBe true
            }

            it("#224 TC-3: a 320 dp page is opaque under Normal margins") {
                ribbonPlacement(320f, 0f, 0f, ReaderMargins.Normal, spread = "none").translucent shouldBe false
            }

            it("#224 TC-3: a 320 dp page is opaque under Wide margins") {
                ribbonPlacement(320f, 0f, 0f, ReaderMargins.Wide, spread = "none").translucent shouldBe false
            }

            it("#224 TC-3: a 411 dp page is opaque under Normal margins") {
                ribbonPlacement(411f, 0f, 0f, ReaderMargins.Normal, spread = "none").translucent shouldBe false
            }

            it("#224 TC-3: a 360 dp page is translucent under Narrow margins") {
                ribbonPlacement(360f, 0f, 0f, ReaderMargins.Narrow, spread = "none").translucent shouldBe true
            }

            it("#224 TC-3: a 360 dp page is opaque under Wide margins") {
                ribbonPlacement(360f, 0f, 0f, ReaderMargins.Wide, spread = "none").translucent shouldBe false
            }
        }
    }

    describe("the ribbon's placement on a spread") {
        context("with the spread on and Normal margins") {
            it("#224 TC-4: a 1020 dp page is two-page, since its column is exactly 800 dp") {
                ribbonPlacement(1020f, 0f, 0f, ReaderMargins.Normal, spread = "auto").twoPage shouldBe true
            }

            it("#224 TC-4: a 1019 dp page is single, since its column is just under 800 dp") {
                ribbonPlacement(1019f, 0f, 0f, ReaderMargins.Normal, spread = "auto").twoPage shouldBe false
            }

            it("#224 TC-4: a two-page ribbon centres on the stage centre, not the window centre") {
                // The stage runs from 40 to 1040 (insets 30 and 10), so its centre is 540 and the ribbon starts 14 dp
                // left of it.
                val placement = ribbonPlacement(1060f, 30f, 10f, ReaderMargins.Normal, spread = "auto")

                placement.startDp shouldBe 526f
            }

            it("#224 TC-4: a two-page ribbon is opaque when the gutter is wide enough") {
                // The column is 800 dp, so its gap is 66 dp, well over the ribbon's 28 dp.
                ribbonPlacement(1060f, 30f, 10f, ReaderMargins.Normal, spread = "auto").translucent shouldBe false
            }
        }

        context("with the spread off") {
            it("#224 TC-4: a 1060 dp page is single, even though it would fit a spread") {
                ribbonPlacement(1060f, 30f, 10f, ReaderMargins.Normal, spread = "none").twoPage shouldBe false
            }
        }
    }
})
