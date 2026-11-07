package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Callbacks. PlayerToolRowScreenshotTest covers how the row looks. */
class PlayerToolRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setToolRow(rate: Double, hasEbook: Boolean, actions: PlayerActions) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    PlayerToolRow(rate = rate, hasEbook = hasEbook, actions = actions)
                }
            }
        }
    }

    // R14: each cell fires its own action once
    @Test
    fun toolCellsInvokeTheirActions() {
        val recorded = RecordedActions()
        setToolRow(rate = 1.0, hasEbook = true, actions = recorded.actions())

        rule.onNodeWithTag(PlayerTestTags.TOOL_SPEED).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_CAR_MODE).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SLEEP).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_BOOKMARK).performClick()
        rule.onNodeWithTag(PlayerTestTags.TOOL_SYNC).performClick()
        rule.waitForIdle()

        assertEquals(1, recorded.speed)
        assertEquals(1, recorded.carMode)
        assertEquals(1, recorded.sleep)
        assertEquals(1, recorded.bookmark)
        assertEquals(1, recorded.sync)
    }
}
