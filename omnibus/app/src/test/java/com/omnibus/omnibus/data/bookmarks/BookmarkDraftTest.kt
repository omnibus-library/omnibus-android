package com.omnibus.omnibus.data.bookmarks

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class BookmarkDraftTest : DescribeSpec({

    /** A surrogate pair: one code point, two UTF-16 units. */
    val book = "📖"

    /** True when every surrogate is part of a valid pair, the check `String.isWellFormed` makes on newer JVMs. */
    fun wellFormed(s: String): Boolean {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                Character.isHighSurrogate(c) -> {
                    if (i + 1 >= s.length || !Character.isLowSurrogate(s[i + 1])) return false
                    i += 2
                }
                Character.isLowSurrogate(c) -> return false
                else -> i++
            }
        }
        return true
    }

    fun codePoints(s: String): Int = s.codePointCount(0, s.length)

    describe("BookmarkDraft.create") {

        // TC-1: whitespace around the position and the chapter title is trimmed; the book uuid is kept.
        it("TC-1: a normal cfi and chapter title give a trimmed draft") {
            val draft = BookmarkDraft.create("book-1", "  epubcfi(/6/4!/4/2:0)\n", "  Chapter One ").shouldNotBeNull()

            draft.bookUuid shouldBe "book-1"
            draft.position shouldBe "epubcfi(/6/4!/4/2:0)"
            draft.title shouldBe "Chapter One"
        }

        // TC-2: a null, empty or whitespace-only position gives no draft.
        it("TC-2: a null or blank position gives no draft") {
            BookmarkDraft.create("book-1", null, "Ch").shouldBeNull()
            BookmarkDraft.create("book-1", "", "Ch").shouldBeNull()
            BookmarkDraft.create("book-1", "   \t", "Ch").shouldBeNull()
        }

        // TC-3: 4096 code points is the largest position; it is kept whole, never truncated.
        it("TC-3: position is accepted at 4096 code points and rejected at 4097") {
            val accepted = "a".repeat(4096)
            BookmarkDraft.create("book-1", accepted, null)?.position shouldBe accepted
            BookmarkDraft.create("book-1", "a".repeat(4097), null).shouldBeNull()
        }

        // TC-4: the limit counts code points, so 4096 astral characters (8192 units) are still accepted.
        it("TC-4: position length counts code points not UTF-16 units") {
            val accepted = book.repeat(4096)
            accepted.length shouldBe 8192
            BookmarkDraft.create("book-1", accepted, null)?.position shouldBe accepted

            BookmarkDraft.create("book-1", book.repeat(4097), null).shouldBeNull()
        }

        // TC-5: a null, empty or whitespace title becomes null, and the draft is still made.
        it("TC-5: a null or blank title becomes null") {
            listOf(null, "", "   ").forEach { title ->
                val draft = BookmarkDraft.create("book-1", "epubcfi(/6)", title).shouldNotBeNull()
                draft.title.shouldBeNull()
            }
        }

        // TC-6: a 512-code-point title is kept as is; a 513-code-point title is cut to its first 512.
        it("TC-6: title is kept at 512 and cut to 512 at 513") {
            val exact = "t".repeat(512)
            BookmarkDraft.create("book-1", "epubcfi(/6)", exact)?.title shouldBe exact

            val over = "t".repeat(512) + "x"
            BookmarkDraft.create("book-1", "epubcfi(/6)", over)?.title shouldBe exact
        }

        // TC-7: a cut never leaves half a surrogate pair. The pair is kept whole when it is the 512th code point, and
        // a pair that would be the 513th is dropped whole.
        it("TC-7: truncation never splits a surrogate pair") {
            // 511 BMP characters, then a pair at units 511 and 512, then more text: the 512th code point is the pair.
            val straddling = "a".repeat(511) + book + "tail"
            val cut = BookmarkDraft.create("book-1", "epubcfi(/6)", straddling).shouldNotBeNull().title.shouldNotBeNull()
            codePoints(cut) shouldBe 512
            wellFormed(cut) shouldBe true
            cut shouldBe "a".repeat(511) + book

            // 512 pairs then one more code point: the cut keeps exactly 512 whole pairs.
            val pairs = book.repeat(512) + "x"
            val pairCut = BookmarkDraft.create("book-1", "epubcfi(/6)", pairs).shouldNotBeNull().title.shouldNotBeNull()
            codePoints(pairCut) shouldBe 512
            wellFormed(pairCut) shouldBe true
            pairCut shouldBe book.repeat(512)
        }

        // TC-8: a blank book uuid gives no draft, even with a valid position.
        it("TC-8: a blank book uuid gives no draft") {
            BookmarkDraft.create("", "epubcfi(/6)", "Ch").shouldBeNull()
            BookmarkDraft.create("   ", "epubcfi(/6)", "Ch").shouldBeNull()
        }
    }
})
