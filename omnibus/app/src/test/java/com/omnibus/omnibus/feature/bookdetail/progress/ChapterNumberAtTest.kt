package com.omnibus.omnibus.feature.bookdetail.progress

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

private fun chapter(index: Int, start: Float) = BookChapter(index, null, start)

class ChapterNumberAtTest : DescribeSpec({

    describe("chapterNumberAt") {

        it("#117 TC-25: a fraction maps to its 1-based chapter, including exact boundaries and the ends") {
            val four = listOf(chapter(0, 0f), chapter(1, 0.25f), chapter(2, 0.5f), chapter(3, 0.75f))
            listOf(
                Triple(four, 0.3f, 2),
                Triple(four, 0.25f, 2),
                Triple(four, 0f, 1),
                Triple(four, 0.75f, 4),
                Triple(four, 1.0f, 4),
                Triple(listOf(chapter(0, 0.1f)), 0.05f, null),
                Triple(listOf(chapter(5, 0f), chapter(6, 0.5f)), 0.2f, 6),
            ).forEach { (chapters, fraction, expected) ->
                chapterNumberAt(fraction, chapters) shouldBe expected
            }
        }

        it("#117 TC-25: chapters sharing a start give the lowest index, in either list order") {
            val pairs = listOf(
                Triple(listOf(chapter(2, 0.4f), chapter(1, 0.4f)), 0.45f, 2),
                Triple(listOf(chapter(1, 0.4f), chapter(2, 0.4f)), 0.45f, 2),
                Triple(listOf(chapter(3, 0.3f), chapter(1, 0.30005f)), 0.31f, 2),
                Triple(listOf(chapter(1, 0.30005f), chapter(3, 0.3f)), 0.31f, 2),
            )
            pairs.forEach { (chapters, fraction, expected) ->
                chapterNumberAt(fraction, chapters) shouldBe expected
            }
        }

        it("#117 TC-25: a null or NaN fraction, or no chapters, has no chapter") {
            val four = listOf(chapter(0, 0f), chapter(1, 0.25f), chapter(2, 0.5f), chapter(3, 0.75f))
            chapterNumberAt(null, four) shouldBe null
            chapterNumberAt(Float.NaN, four) shouldBe null
            chapterNumberAt(0.3f, emptyList()) shouldBe null
        }
    }
})
