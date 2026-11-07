package com.omnibus.omnibus.feature.library.ui

import com.omnibus.omnibus.ui.theme.OmnibusPalette
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ContinueCardGroundTest : DescribeSpec({

    describe("whether a theme's ground is light") {
        it("#22 TC-16: is true for the light palette, false for the dark one") {
            isLightGround(OmnibusPalette.Light.bg0) shouldBe true
            isLightGround(OmnibusPalette.Atrium.bg0) shouldBe false
        }
    }
})
