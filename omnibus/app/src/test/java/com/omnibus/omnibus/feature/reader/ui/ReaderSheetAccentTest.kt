package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class ReaderSheetAccentTest : DescribeSpec({

    val redCover = CoverIdentity(uuid = "red", title = "Red", accent = "oklch(0.55 0.2 25)", hasCover = true)
    val blueCover = CoverIdentity(uuid = "blue", title = "Blue", accent = "oklch(0.55 0.2 250)", hasCover = true)

    describe("readerSheetAccent") {

        it("#201 AC3: two covers give two accents on the same app theme") {
            val red = readerSheetAccent(ReaderBookState.Ready("Red", redCover), OmnibusPalette.Light)
            val blue = readerSheetAccent(ReaderBookState.Ready("Blue", blueCover), OmnibusPalette.Light)

            red.tint shouldNotBe blue.tint
            red.tintSoft shouldNotBe blue.tintSoft
        }

        it("#201: a ready book's accent takes its ground from the app theme") {
            val book = ReaderBookState.Ready("Red", redCover)

            readerSheetAccent(book, OmnibusPalette.Light) shouldBe bookAccent(redCover.tone, lightGround = true)
            readerSheetAccent(book, OmnibusPalette.Atrium) shouldBe bookAccent(redCover.tone, lightGround = false)
        }

        it("#201: a book without a cover yet keeps the app accent") {
            val palette = OmnibusPalette.Light

            val accent = readerSheetAccent(ReaderBookState.Loading, palette)

            accent.tint shouldBe palette.accent
            accent.onTint shouldBe palette.accentInk
        }
    }
})
