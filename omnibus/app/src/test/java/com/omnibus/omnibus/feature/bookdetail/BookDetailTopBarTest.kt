package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.animation.core.SnapSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.ui.TOP_BAR_FILL_ALPHA
import com.omnibus.omnibus.feature.bookdetail.ui.TopBarFillThreshold
import com.omnibus.omnibus.feature.bookdetail.ui.TopBarRowHeight
import com.omnibus.omnibus.feature.bookdetail.ui.isTopBarFilled
import com.omnibus.omnibus.feature.bookdetail.ui.topBarFillSpec
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BookDetailTopBarTest : DescribeSpec({

    describe("isTopBarFilled") {

        it("#93 TC-28: fills only past the threshold") {
            for (scroll in listOf(0, 299, 300)) {
                isTopBarFilled(scroll, 300f) shouldBe false
            }
            for (scroll in listOf(301, 10_000)) {
                isTopBarFilled(scroll, 300f) shouldBe true
            }
            isTopBarFilled(450, 450f) shouldBe false
            isTopBarFilled(451, 450f) shouldBe true
        }
    }

    describe("top bar constants") {

        it("#93 TC-29: fill alpha, threshold and row height") {
            TOP_BAR_FILL_ALPHA shouldBe 0.92f
            TopBarFillThreshold shouldBe 300.dp
            TopBarRowHeight shouldBe 50.dp
        }
    }

    describe("topBarFillSpec") {

        it("#93 TC-101: reduced motion snaps the fill") {
            topBarFillSpec(reducedMotion = true).shouldBeInstanceOf<SnapSpec<Float>>()

            val tween = topBarFillSpec(reducedMotion = false).shouldBeInstanceOf<TweenSpec<Float>>()
            tween.durationMillis shouldBe 200
        }
    }
})
