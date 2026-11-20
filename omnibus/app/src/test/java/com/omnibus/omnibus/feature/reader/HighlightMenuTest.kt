package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.AnnotationHit
import com.omnibus.omnibus.feature.reader.ui.AnnotationTapData
import com.omnibus.omnibus.feature.reader.ui.randomAnnotationHit
import com.omnibus.omnibus.feature.reader.ui.randomCfi
import com.omnibus.omnibus.feature.reader.ui.randomPageRect
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class HighlightMenuTest : DescribeSpec({

    describe("building the menu from a tap") {
        context("when some hits are unknown or have no rects") {
            it("#299 TC-4: drops unknown and rect-less hits") {
                val unknown = randomAnnotationHit()
                val rectless = randomAnnotationHit().copy(rects = emptyList())
                val nullText = randomAnnotationHit()
                val withText = randomAnnotationHit()
                val known = mapOf(
                    rectless.cfi to randomKnownHighlight(),
                    nullText.cfi to randomKnownHighlight().copy(text = null),
                    withText.cfi to randomKnownHighlight().copy(text = randomString()),
                )

                val menu = highlightMenuOf(AnnotationTapData(hits = listOf(unknown, rectless, nullText, withText)), known)

                menu.shouldNotBeNull().entries.map { it.cfiRange } shouldBe listOf(nullText.cfi, withText.cfi)
            }
        }

        context("when no hit is known") {
            it("#299 TC-4: is null when no hit is known") {
                val tap = AnnotationTapData(hits = listOf(randomAnnotationHit(), randomAnnotationHit()))

                highlightMenuOf(tap, mapOf(randomCfi() to randomKnownHighlight())).shouldBeNull()
            }
        }

        context("when every hit is known") {
            it("#299 TC-5: keeps the glue's order") {
                val earlier = randomAnnotationHit()
                val middle = randomAnnotationHit()
                val later = randomAnnotationHit()
                val tap = AnnotationTapData(hits = listOf(later, earlier, middle))

                highlightMenuOf(tap, knownOf(earlier, middle, later)).shouldNotBeNull().entries.map { it.cfiRange } shouldBe
                    listOf(later.cfi, earlier.cfi, middle.cfi)
            }

            it("#299 TC-5: lists a range once") {
                val earlier = randomAnnotationHit()
                val middle = randomAnnotationHit()
                val repeated = earlier.copy(rects = List(randomInt(1..3)) { randomPageRect() })
                val tap = AnnotationTapData(hits = listOf(earlier, repeated, middle))

                highlightMenuOf(tap, knownOf(earlier, middle)).shouldNotBeNull().entries.map { it.cfiRange } shouldBe
                    listOf(earlier.cfi, middle.cfi)
            }

            it("#332 TC-2: carries each highlight's note into its entry") {
                val earlier = randomAnnotationHit()
                val later = randomAnnotationHit()
                val known = mapOf(
                    earlier.cfi to randomKnownHighlight().copy(note = randomString()),
                    later.cfi to randomKnownHighlight().copy(note = null),
                )
                val tap = AnnotationTapData(hits = listOf(later, earlier))

                val menu = highlightMenuOf(tap, known).shouldNotBeNull()

                menu.entries.associate { it.cfiRange to it.note } shouldBe
                    known.mapValues { (_, highlight) -> highlight.note }
            }

            it("#299 TC-5: opens on the first highlight") {
                val earlier = randomAnnotationHit()
                val later = randomAnnotationHit()
                val tap = AnnotationTapData(hits = listOf(later, earlier))

                highlightMenuOf(tap, knownOf(earlier, later)).shouldNotBeNull().active shouldBe 0
            }
        }
    }

    describe("stepping the active highlight") {
        context("when the active highlight is in the middle") {
            it("#299 TC-6: moves one step forward") {
                val size = randomInt(3..6)
                val menu = menuOf(size, active = randomInt(1..size - 2))

                menu.stepped(1).active shouldBe menu.active + 1
            }

            it("#299 TC-6: moves one step back") {
                val size = randomInt(3..6)
                val menu = menuOf(size, active = randomInt(1..size - 2))

                menu.stepped(-1).active shouldBe menu.active - 1
            }
        }

        context("when the active highlight is the last") {
            it("#299 TC-6: wraps forward to the first") {
                val size = randomInt(2..6)
                val menu = menuOf(size, active = size - 1)

                menu.stepped(1).active shouldBe 0
            }
        }

        context("when the active highlight is the first") {
            it("#299 TC-6: wraps back to the last") {
                val menu = menuOf(randomInt(2..6), active = 0)

                menu.stepped(-1).active shouldBe menu.count - 1
            }
        }

        context("when there is only one highlight") {
            it("#299 TC-6: stays on it in either direction") {
                val menu = menuOf(1, active = 0)

                checkAll(Exhaustive.of(1, -1)) { delta ->
                    menu.stepped(delta).active shouldBe 0
                }
            }
        }
    }

    describe("numbering the highlights") {
        it("#299 TC-6: counts every highlight") {
            val size = randomInt(1..6)

            menuOf(size, active = 0).count shouldBe size
        }

        it("#299 TC-6: numbers the active highlight from one") {
            val size = randomInt(2..6)
            val menu = menuOf(size, active = randomInt(0..size - 1))

            menu.position shouldBe menu.active + 1
        }
    }
})

/** A menu of [count] random highlights with [active] shown. */
private fun menuOf(count: Int, active: Int): HighlightMenu =
    HighlightMenu(List(count) { randomHighlightMenuEntry() }, active)

/** A known highlight, with random colour and text, for each of [hits]. */
private fun knownOf(vararg hits: AnnotationHit): Map<String, KnownHighlight> =
    hits.associate { it.cfi to randomKnownHighlight() }

private val AnnotationHit.cfi: String get() = cfiRange!!
