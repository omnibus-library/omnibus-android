package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.feature.bookdetail.stage.NearBlackInk
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.ui.theme.contrastRatio
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class BookAccentTest : DescribeSpec({

    describe("bookAccent") {

        it("#93 TC-35: tint and tintSoft") {
            val tone = Oklch(0.5, 0.1, 150.0)

            val dark = bookAccent(tone, lightGround = false)
            dark.tint shouldBe Oklch(0.62, 0.1, 150.0).toColor()
            dark.tintSoft shouldBe Oklch(0.62, 0.1, 150.0, alpha = 0.16).toColor()

            val light = bookAccent(tone, lightGround = true)
            light.tint shouldBe Oklch(0.55, 0.1, 150.0).toColor()
            light.tintSoft shouldBe Oklch(0.62, 0.1, 150.0, alpha = 0.16).toColor()
        }

        it("#93 TC-36: onTint has the higher contrast") {
            for (light in listOf(false, true)) {
                for (hue in 0..330 step 30) {
                    for (chroma in listOf(0.0, 0.1, 0.2)) {
                        val accent = bookAccent(Oklch(0.5, chroma, hue.toDouble()), lightGround = light)
                        val other = if (accent.onTint == Color.White) NearBlackInk else Color.White

                        listOf(Color.White, NearBlackInk) shouldContain accent.onTint
                        (contrastRatio(accent.onTint, accent.tint) >= contrastRatio(other, accent.tint)) shouldBe true
                    }
                }
            }
        }

        it("#93 TC-37: achromatic tone") {
            for (lightness in listOf(0.0, 0.5, 1.0)) {
                val accent = bookAccent(Oklch(lightness, 0.0, 0.0), lightGround = false)

                listOf(accent.tint.red, accent.tint.green, accent.tint.blue, accent.tintSoft.alpha)
                    .all { it.isFinite() } shouldBe true
                listOf(Color.White, NearBlackInk) shouldContain accent.onTint
            }
            contrastRatio(Color.White, Color.Black) shouldBe (21.0 plusOrMinus 0.1)
        }
    }
})
