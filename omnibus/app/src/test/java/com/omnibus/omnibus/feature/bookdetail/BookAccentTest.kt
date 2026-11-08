package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.feature.bookdetail.stage.NearBlackInk
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.ui.theme.contrastRatio
import com.omnibus.omnibus.ui.theme.Oklch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class BookAccentTest : DescribeSpec({

    describe("the ink on a book's accent") {

        it("#93 TC-36 / TC-37: has the higher contrast of white or near-black, across ground, lightness, chroma and hue") {
            checkAll(
                Exhaustive.boolean(),
                Exhaustive.of(0.0, 0.5, 1.0),
                Exhaustive.of(0.0, 0.1, 0.2),
                Exhaustive.of(0.0, 90.0, 180.0, 270.0),
            ) { lightGround, lightness, chroma, hue ->
                val accent = bookAccent(Oklch(lightness, chroma, hue), lightGround = lightGround)
                val other = if (accent.onTint == Color.White) NearBlackInk else Color.White

                (contrastRatio(accent.onTint, accent.tint) >= contrastRatio(other, accent.tint)) shouldBe true
            }
        }
    }
})
