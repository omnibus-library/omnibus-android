package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ReaderSheetAccentTest : DescribeSpec({

    describe("the accent of the reader's sheets") {
        context("with a ready book") {
            it("#201 AC3: is the cover's accent, on the ground of the app theme") {
                val cover = CoverIdentity(
                    uuid = randomString(),
                    title = randomString(),
                    accent = "oklch(0.55 0.2 ${randomInt(0..359)})",
                    hasCover = true,
                )
                val book = ReaderBookState.Ready(randomString(), cover)

                readerSheetAccent(book, OmnibusPalette.Light) shouldBe bookAccent(cover.tone, lightGround = true)
                readerSheetAccent(book, OmnibusPalette.Atrium) shouldBe bookAccent(cover.tone, lightGround = false)
            }
        }

        context("with a book that has no cover yet") {
            it("#201: is the app accent") {
                val palette = OmnibusPalette.Light

                val accent = readerSheetAccent(ReaderBookState.Loading, palette)

                accent.tint shouldBe palette.accent
                accent.onTint shouldBe palette.accentInk
            }
        }
    }
})
