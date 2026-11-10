package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.slots.BookDetailSlots
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailScreenContent
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellLayoutInsetsTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // TC-3
    @Test
    fun tc3_overlayInsetEqualsMeasuredOverlayHeight() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val grown = mutableStateOf(false)
        var inset = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellLayout(overlay = { ShellTestOverlay(grown = grown.value) }, modifier = Modifier.fillMaxSize()) {
                    inset = WindowInsets.bottomOverlay.getBottom(LocalDensity.current)
                }
            }
        }
        rule.waitForIdle()

        val before = rule.onNodeWithTag(SHELL_OVERLAY_TAG).bounds().height
        assertEquals(before, inset.toFloat(), 1f)

        rule.runOnIdle { grown.value = true }
        rule.waitForIdle()

        val after = rule.onNodeWithTag(SHELL_OVERLAY_TAG).bounds().height
        assertTrue("the overlay did not grow: $before -> $after", after > before)
        assertEquals(after, inset.toFloat(), 1f)
    }

    // TC-9
    @Test
    fun tc9_paddedWrapperFillsBetweenStatusBarAndOverlay() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        var statusTop = 0
        rule.setContent {
            statusTop = WindowInsets.statusBars.getTop(LocalDensity.current)
            OmnibusTheme(darkTheme = true) {
                ShellLayout(overlay = { ShellTestOverlay() }, modifier = Modifier.fillMaxSize()) {
                    ShellPaddedEntry { Box(Modifier.fillMaxSize().testTag(ENTRY_TAG)) }
                }
            }
        }
        rule.waitForIdle()

        val entry = rule.onNodeWithTag(ENTRY_TAG).bounds()
        val overlayTop = rule.onNodeWithTag(SHELL_OVERLAY_TAG).bounds().top
        assertEquals(statusTop.toFloat(), entry.top, 1f)
        assertEquals(overlayTop, entry.bottom, 1f)
    }

    // TC-10
    @Test
    fun tc10_bookDetailActionAreaClearsTheOverlay() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellLayout(overlay = { ShellTestOverlay() }, modifier = Modifier.fillMaxSize()) {
                    ShellUnderStatusBarEntry {
                        BookDetailScreenContent(
                            uiState = BookDetailUIState(book = testDetailBook(), isLoading = false),
                            onNavigate = {},
                            onClose = {},
                            onRetry = {},
                            onRefresh = {},
                            onReloadBook = {},
                            onBookChanged = {},
                            onTransientErrorShown = {},
                            modifier = Modifier.fillMaxSize(),
                            slots = BookDetailSlots(),
                            scrollState = ScrollState(0),
                            reducedMotion = true,
                        )
                    }
                }
            }
        }
        rule.waitForIdle()

        val actionArea = rule.onNodeWithTag(BookDetailTestTags.ACTION_AREA).bounds()
        val overlayTop = rule.onNodeWithTag(SHELL_OVERLAY_TAG).bounds().top
        assertTrue(
            "the action area ends at ${actionArea.bottom}, below the overlay's top at $overlayTop",
            actionArea.bottom <= overlayTop + 1f,
        )
    }

    private companion object {
        const val ENTRY_TAG = "shell-test-entry"
    }
}
