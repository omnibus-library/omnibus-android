package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.shell.chrome.FabSpec
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellFabTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc51_fabHasLabelAndInvokesCallbackOnClick() {
        // TC-38: the label names the button, its role is Button, and a click invokes the callback once
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellFab(FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books) { taps++ })
            }
        }

        val fab = rule.onNodeWithContentDescription(ADD_LABEL)
        fab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        fab.performClick()

        assertEquals(1, taps)
    }

    @Test
    fun tc52_fabPerformsOneLightHapticPerTap() {
        // TC-39: one VirtualKey tick per tap, recorded before the callback
        val events = mutableListOf<Any>()
        val haptics = object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                events += hapticFeedbackType
            }
        }
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OmnibusTheme(darkTheme = true) {
                    ShellFab(FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books) { events += "click" })
                }
            }
        }

        rule.onNodeWithContentDescription(ADD_LABEL).performClick()

        assertEquals(listOf<Any>(HapticFeedbackType.VirtualKey, "click"), events)
    }

    @Test
    fun disabledFabIgnoresTapsAndTargetIsLargeEnough() {
        // TC-40: a disabled FAB takes no taps, and its target is at least 48 dp
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellFab(
                    spec = FabSpec(R.drawable.ic_add_book, R.string.masthead_add_books) { taps++ },
                    enabled = false,
                )
            }
        }

        val fab = rule.onNodeWithContentDescription(ADD_LABEL)
        fab.performClick()
        assertEquals(0, taps)

        val bounds = fab.getBoundsInRoot()
        assertTrue("the FAB is ${bounds.widthDp} wide", bounds.widthDp >= 48.dp)
        assertTrue("the FAB is ${bounds.heightDp} tall", bounds.heightDp >= 48.dp)
    }

    private val DpRect.widthDp get() = right - left

    private val DpRect.heightDp get() = bottom - top

    private companion object {
        const val ADD_LABEL = "Add books by upload or barcode scan"
    }
}
