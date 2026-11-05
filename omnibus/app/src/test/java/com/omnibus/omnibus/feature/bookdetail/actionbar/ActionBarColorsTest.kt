package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.feature.bookdetail.status.statusRim
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ActionBarColorsTest : DescribeSpec({

    describe("the action bar colours") {

        it("TC-23: colours come from the cover tone at the deep and fill lightness") {
            val tones = listOf(Oklch(0.7, 0.13, 28.0), Oklch(0.6, 0.1, 150.0))
            val colours = tones.map { tone ->
                val colors = actionBarColors(tone)
                colors.base shouldBe Oklch(0.44, tone.c, tone.h).toColor()
                colors.fill shouldBe Oklch(0.52, tone.c, tone.h).toColor()
                colors.content shouldBe Color.White
                colors
            }
            (colours[0].base == colours[1].base) shouldBe false

            val book = testBook(1)
            actionBarColors(book) shouldBe actionBarColors(book.coverIdentity().tone)
        }
    }

    describe("the listen button colours") {

        it("#99 TC-11: the wash is the tone at lightness .62 and alpha .16, and the rim is the Check in pill's") {
            val tone = Oklch(0.7, 0.13, 28.0)
            for (lightGround in listOf(false, true)) {
                val colors = listenButtonColors(tone, lightGround)
                colors.wash shouldBe Oklch(0.62, tone.c, tone.h, alpha = 0.16).toColor()
                colors.rim shouldBe statusRim(tone, lightGround)
            }
        }
    }
})
