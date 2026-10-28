package com.omnibus.omnibus.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class TypeTest : DescribeSpec({

    describe("displayTextStyle") {

        it("TC-34: maps weights and enables lining figures; Int sizes keep their output") {
            displayTextStyle(16, Color.White, FontWeight.Medium).fontWeight shouldBe FontWeight.Medium
            displayTextStyle(16.sp, Color.White, FontWeight.SemiBold).fontWeight shouldBe FontWeight.SemiBold
            displayTextStyle(16, Color.White).fontWeight shouldBe FontWeight.Normal

            displayTextStyle(16, Color.White).fontFeatureSettings shouldBe "lnum"

            val fromInt = displayTextStyle(16, Color.White)
            val fromSp = displayTextStyle(16.sp, Color.White)
            fromInt.fontSize shouldBe 16.sp
            fromInt.fontFamily shouldBe DisplayFontFamily
            fromInt.fontSize shouldBe fromSp.fontSize
            fromInt.fontFamily shouldBe fromSp.fontFamily
            fromInt.fontFeatureSettings shouldBe fromSp.fontFeatureSettings
        }
    }

    describe("DisplayFontFamily") {

        it("TC-35: bundles Normal, Medium and SemiBold") {
            val weights = (DisplayFontFamily as FontListFontFamily).fonts.map { it.weight }.toSet()

            weights shouldBe setOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold)
        }
    }
})
