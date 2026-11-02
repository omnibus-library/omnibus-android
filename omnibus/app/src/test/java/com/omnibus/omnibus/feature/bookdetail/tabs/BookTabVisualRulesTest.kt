package com.omnibus.omnibus.feature.bookdetail.tabs

import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.abs

class BookTabVisualRulesTest : DescribeSpec({

    describe("tab visual rules") {

        it("#94 TC-16: ribbon centre sits in the middle of its cell") {
            // width 1000, inset 48: cell = (1000 - 96) / 4 = 226.
            val centres = (0..3).map { ribbonCenterXPx(it.toFloat(), 1000f, 48f, tabCount = 4) }
            centres[0] shouldBe (161f plusOrMinus 1e-3f)
            centres[3] shouldBe (839f plusOrMinus 1e-3f)
            centres.zipWithNext().forEach { (a, b) ->
                (b - a) shouldBe (226f plusOrMinus 1e-3f)
            }
            // Symmetric about the centre: the first and last centres mirror each other.
            (centres[0] + centres[3]) shouldBe (1000f plusOrMinus 1e-3f)
        }

        it("#94 TC-17: ribbon swing mirrors with direction") {
            val right = ribbonSwingKeyframes(1)
            right shouldBe listOf(0 to 0f, 200 to -10f, 520 to 4f, 900 to 0f)
            val left = ribbonSwingKeyframes(-1)
            left.map { it.first } shouldBe right.map { it.first }
            left.zip(right).forEach { (l, r) ->
                l.second shouldBe (-r.second plusOrMinus 1e-6f)
            }
            left[1].second shouldBe (10f plusOrMinus 1e-6f)
            left[2].second shouldBe (-4f plusOrMinus 1e-6f)
        }

        it("#94 TC-18: panel turn pose interpolates from the tapped side") {
            for (a in listOf(-1f, -0.5f, 0f, 0.5f, 1f)) {
                val pose = panelTurnPose(a)
                pose.alpha shouldBe (1f - abs(a) plusOrMinus 1e-6f)
                pose.rotationYDegrees shouldBe (-6f * a plusOrMinus 1e-6f)
                pose.offsetDp shouldBe (40f * a plusOrMinus 1e-6f)
            }
            val rest = panelTurnPose(0f)
            rest.alpha shouldBe (1f plusOrMinus 1e-6f)
            rest.rotationYDegrees shouldBe (0f plusOrMinus 1e-6f)
            rest.offsetDp shouldBe (0f plusOrMinus 1e-6f)

            val fromRight = panelTurnPose(1f)
            fromRight.offsetDp shouldBe (40f plusOrMinus 1e-6f)
            fromRight.rotationYDegrees shouldBe (-6f plusOrMinus 1e-6f)
            fromRight.alpha shouldBe (0f plusOrMinus 1e-6f)
            panelTurnPose(-1f).offsetDp shouldBe (-40f plusOrMinus 1e-6f)
        }

        it("#94 TC-19: wash pan and warmth per tab match iOS") {
            val tabs = BookDetailTab.entries
            val pans = tabs.map { tabWashPan(it) }
            pans[0] shouldBe (0.165f plusOrMinus 1e-6f)
            pans[1] shouldBe (0.055f plusOrMinus 1e-6f)
            pans[2] shouldBe (-0.055f plusOrMinus 1e-6f)
            pans[3] shouldBe (-0.165f plusOrMinus 1e-6f)
            pans.zipWithNext().forEach { (a, b) -> (b < a) shouldBe true }

            tabs.map { tabWashWarmth(it) } shouldBe listOf(0.5f, 0.5f, 1.0f, 0.1f)
        }
    }
})
