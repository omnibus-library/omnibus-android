package com.omnibus.omnibus.feature.requests

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private class RecordingLifecycleOwner : LifecycleOwner {
    override val lifecycle = LifecycleRegistry.createUnsafe(this)
}

class PollWhileStartedTest {

    @get:Rule
    val rule = createComposeRule()

    private val owner = RecordingLifecycleOwner()
    private var running = 0
    private var runs = 0

    private fun showScreen() {
        rule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                PollWhileStarted {
                    runs++
                    running++
                    try {
                        awaitCancellation()
                    } finally {
                        running--
                    }
                }
            }
        }
    }

    private fun moveTo(state: Lifecycle.State) {
        rule.runOnUiThread { owner.lifecycle.currentState = state }
        rule.waitForIdle()
    }

    @Test
    fun pollWhileStarted_stopsPollingWhenTheLifecycleLeavesStarted() {
        moveTo(Lifecycle.State.RESUMED)
        showScreen()
        assertEquals(1, running)

        moveTo(Lifecycle.State.CREATED)

        assertEquals(0, running)
    }

    @Test
    fun pollWhileStarted_pollsAgainWhenTheLifecycleReturnsToStarted() {
        moveTo(Lifecycle.State.RESUMED)
        showScreen()
        moveTo(Lifecycle.State.CREATED)

        moveTo(Lifecycle.State.STARTED)

        assertEquals(listOf(2, 1), listOf(runs, running))
    }
}
