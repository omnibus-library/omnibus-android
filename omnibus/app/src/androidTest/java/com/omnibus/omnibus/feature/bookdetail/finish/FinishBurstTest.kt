package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The accent the burst is drawn in for these tests. */
private val burstAccent = Color(0xFFB0703A)

class FinishBurstTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue97_tc37_theBurstShowsAndATapOnTheCoverReplaysIt() {
        var replays = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(0.5f).aspectRatio(2f / 3f)) {
                    FinishBurst(
                        finished = true,
                        bursts = 1,
                        reducedMotion = false,
                        accent = burstAccent,
                        latch = PlayLatch(),
                        onReplay = { replays++ },
                    )
                }
            }
        }

        rule.onNodeWithTag(FinishTestTags.BURST).assertExists()
        rule.onNodeWithTag(FinishTestTags.COVER_TAP).performTouchInput { click() }

        rule.runOnIdle { assertEquals(1, replays) }
    }

    @Test
    fun issue97_tc38_noBurstBeforeACelebration() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(0.5f).aspectRatio(2f / 3f)) {
                    FinishBurst(
                        finished = true,
                        bursts = 0,
                        reducedMotion = false,
                        accent = burstAccent,
                        latch = PlayLatch(),
                        onReplay = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(FinishTestTags.BURST).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.COVER_TAP).assertDoesNotExist()
    }

    @Test
    fun issue97_tc38_noBurstWhenTheBookIsNotFinished() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(0.5f).aspectRatio(2f / 3f)) {
                    FinishBurst(
                        finished = false,
                        bursts = 1,
                        reducedMotion = false,
                        accent = burstAccent,
                        latch = PlayLatch(),
                        onReplay = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(FinishTestTags.BURST).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.COVER_TAP).assertDoesNotExist()
    }

    @Test
    fun issue97_tc38_noBurstWithReducedMotion() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth(0.5f).aspectRatio(2f / 3f)) {
                    FinishBurst(
                        finished = true,
                        bursts = 1,
                        reducedMotion = true,
                        accent = burstAccent,
                        latch = PlayLatch(),
                        onReplay = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(FinishTestTags.BURST).assertDoesNotExist()
        rule.onNodeWithTag(FinishTestTags.COVER_TAP).assertDoesNotExist()
    }

    @Test
    fun issue97_tc39_eachThrowPlaysOnce() {
        val latch = PlayLatch()
        var bursts by mutableIntStateOf(1)
        var ticks by mutableIntStateOf(0)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column {
                    Text("tick $ticks")
                    Box(Modifier.fillMaxWidth(0.5f).aspectRatio(2f / 3f)) {
                        FinishBurst(
                            finished = true,
                            bursts = bursts,
                            reducedMotion = false,
                            accent = burstAccent,
                            latch = latch,
                            onReplay = {},
                        )
                    }
                }
            }
        }

        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertFalse(latch.isPending(1)) }

        rule.runOnIdle { ticks++ }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertFalse(latch.isPending(1)) }

        rule.runOnIdle { bursts = 2 }
        rule.mainClock.advanceTimeByFrame()
        rule.runOnIdle { assertFalse(latch.isPending(2)) }
    }
}
