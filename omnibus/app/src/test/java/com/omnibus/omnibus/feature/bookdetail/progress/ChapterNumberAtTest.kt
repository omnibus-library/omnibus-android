package com.omnibus.omnibus.feature.bookdetail.progress

import com.omnibus.omnibus.data.bookprogress.chapters
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun chapter(index: Int, start: Float) = BookChapter(index, null, start)

class ChapterNumberAtTest : DescribeSpec({

    describe("the chapter number at a fraction") {
        it("#117 TC-25: is 1-based, or none, for each kind of input") {
            val four = chapters(0f, 0.25f, 0.5f, 0.75f)

            checkAll(
                Exhaustive.of(
                    Triple(four, 0.3f, 2),
                    Triple(four, 0.25f, 2),
                    Triple(four, 0f, 1),
                    Triple(four, 1f, 4),
                    Triple(listOf(chapter(0, 0.1f)), 0.05f, null),
                    Triple(listOf(chapter(5, 0f), chapter(6, 0.5f)), 0.2f, 6),
                    Triple(four, null, null),
                    Triple(four, Float.NaN, null),
                    Triple(emptyList(), 0.3f, null),
                ),
            ) { (list, fraction, number) ->
                chapterNumberAt(fraction, list) shouldBe number
            }
        }

        it("#117 TC-25: is the lowest index of chapters sharing a start, in either list order") {
            checkAll(
                Exhaustive.of(
                    listOf(chapter(2, 0.4f), chapter(1, 0.4f)) to 0.45f,
                    listOf(chapter(1, 0.4f), chapter(2, 0.4f)) to 0.45f,
                    listOf(chapter(3, 0.3f), chapter(1, 0.30005f)) to 0.31f,
                    listOf(chapter(1, 0.30005f), chapter(3, 0.3f)) to 0.31f,
                ),
            ) { (list, fraction) ->
                chapterNumberAt(fraction, list) shouldBe 2
            }
        }
    }
})
