package com.omnibus.omnibus.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MastheadTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun tc49_mastheadIsOneHeadingWithWordmarkAndTitle() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Masthead(title = "Library")
            }
        }

        rule.onNode(isHeading())
            .assertTextContains("Omnibus", substring = true)
            .assertTextContains("Library", substring = true)
    }

    @Test
    fun tc51_addButtonHasLabelAndInvokesCallbackOnClick() {
        var taps = 0
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                MastheadAddButton(onAddBooks = { taps++ })
            }
        }

        rule.onNodeWithContentDescription(ADD_LABEL).performClick()
        assertEquals(1, taps)
    }

    @Test
    fun tc52_addButtonPerformsOneLightHapticPerTap() {
        val haptics = RecordingHaptics()
        rule.setContent {
            CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                OmnibusTheme(darkTheme = true) {
                    MastheadAddButton(onAddBooks = {})
                }
            }
        }

        rule.onNodeWithContentDescription(ADD_LABEL).performClick()
        assertEquals(listOf(HapticFeedbackType.VirtualKey), haptics.performed)
    }

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private companion object {
        const val ADD_LABEL = "Add books by upload or barcode scan"
    }
}
