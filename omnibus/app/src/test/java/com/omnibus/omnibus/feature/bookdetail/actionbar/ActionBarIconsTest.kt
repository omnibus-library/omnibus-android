package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.VectorPath
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ActionBarIconsTest : DescribeSpec({

    describe("the immersive read icon") {

        it("#99 TC-43: a 24 by 24 viewport with two round-capped, round-joined, unfilled strokes") {
            ImmersiveReadIcon.viewportWidth shouldBe 24f
            ImmersiveReadIcon.viewportHeight shouldBe 24f

            val paths = ImmersiveReadIcon.root.filterIsInstance<VectorPath>()
            paths shouldHaveSize 2
            for (path in paths) {
                path.strokeLineWidth shouldBe 1.7f
                path.strokeLineCap shouldBe StrokeCap.Round
                path.strokeLineJoin shouldBe StrokeJoin.Round
                path.fill shouldBe null
            }
        }
    }
})
