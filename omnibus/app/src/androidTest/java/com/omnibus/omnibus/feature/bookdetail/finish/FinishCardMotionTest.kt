package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** The card's model for the motion tests: a titled card with no row. */
private val motionModel = FinishCardModel("The Hound", emptyList(), null)

/** The card's ground for the motion tests. */
private val motionGround = finishGround(Oklch(0.62, 0.12, 60.0), lightGround = false)

class FinishCardMotionTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue97_tc35_aCelebratedCardRisesIn() {
        val latch = PlayLatch()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    RisingFinishCard(
                        model = motionModel,
                        ground = motionGround,
                        rises = 1,
                        latch = latch,
                        reducedMotion = false,
                        onLeaveReview = {},
                        onOpenBook = {},
                    )
                }
            }
        }

        rule.mainClock.advanceTimeByFrame()
        val risingTop = rule.onNodeWithTag(FinishTestTags.CARD).bounds().top
        rule.mainClock.advanceTimeBy(1_000)
        val settledTop = rule.onNodeWithTag(FinishTestTags.CARD).bounds().top

        assertTrue("rising top $risingTop should be below settled top $settledTop", risingTop > settledTop + 1f)
        assertFalse(latch.isPending(1))
    }

    @Test
    fun issue97_tc36_reducedMotionDoesNotRise() {
        val latch = PlayLatch()
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    RisingFinishCard(
                        model = motionModel,
                        ground = motionGround,
                        rises = 1,
                        latch = latch,
                        reducedMotion = true,
                        onLeaveReview = {},
                        onOpenBook = {},
                    )
                }
            }
        }

        rule.mainClock.advanceTimeByFrame()
        val firstTop = rule.onNodeWithTag(FinishTestTags.CARD).bounds().top
        rule.mainClock.advanceTimeBy(1_000)
        val settledTop = rule.onNodeWithTag(FinishTestTags.CARD).bounds().top

        assertTrue("first $firstTop, settled $settledTop", abs(firstTop - settledTop) <= 0.5f)
        assertFalse(latch.isPending(1))
    }

    @Test
    fun issue97_tc36_anOldPlayDoesNotRise() {
        val latch = PlayLatch()
        assertTrue(latch.claim(1))
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxSize()) {
                    RisingFinishCard(
                        model = motionModel,
                        ground = motionGround,
                        rises = 1,
                        latch = latch,
                        reducedMotion = false,
                        onLeaveReview = {},
                        onOpenBook = {},
                    )
                }
            }
        }

        rule.mainClock.advanceTimeByFrame()
        val firstTop = rule.onNodeWithTag(FinishTestTags.CARD).bounds().top
        rule.mainClock.advanceTimeBy(1_000)
        val settledTop = rule.onNodeWithTag(FinishTestTags.CARD).bounds().top

        assertTrue("first $firstTop, settled $settledTop", abs(firstTop - settledTop) <= 0.5f)
        assertFalse(latch.isPending(1))
    }
}
