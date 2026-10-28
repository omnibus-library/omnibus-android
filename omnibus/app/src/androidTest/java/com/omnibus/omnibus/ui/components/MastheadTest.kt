package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
    fun tc50_accentRuleLeadsIntoHairlineAcrossWidth() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, 200.dp).testTag(CONTAINER)) {
                    Masthead(title = "Library")
                }
            }
        }

        val container = rule.onNodeWithTag(CONTAINER, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val accent = rule.onNodeWithTag(MastheadTestTags.ACCENT_RULE, useUnmergedTree = true)
        val hairline = rule.onNodeWithTag(MastheadTestTags.HAIRLINE, useUnmergedTree = true)
        val accentBounds = accent.getUnclippedBoundsInRoot()
        val hairlineBounds = hairline.getUnclippedBoundsInRoot()

        accent.assertWidthIsEqualTo(28.dp).assertHeightIsEqualTo(1.5.dp)
        hairline.assertHeightIsEqualTo(0.5.dp)
        assertNear(20.dp, accentBounds.left - container.left, "rule starts at 20 dp inset")
        assertNear(accentBounds.right, hairlineBounds.left, "hairline starts where the rule ends")
        assertNear(container.right - 20.dp, hairlineBounds.right, "hairline ends at 20 dp inset")
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

    @Test
    fun tc53_addButtonIsThirtyTwoDpInDefaultMasthead() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Masthead(title = "Library") {
                    MastheadAddButton(onAddBooks = {})
                }
            }
        }

        rule.onNodeWithContentDescription(ADD_LABEL, useUnmergedTree = true)
            .assertWidthIsEqualTo(32.dp)
            .assertHeightIsEqualTo(32.dp)
    }

    @Test
    fun tc54_trailingItemsLayOutLeftToRightEightDpApart() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.size(390.dp, 200.dp).testTag(CONTAINER)) {
                    Masthead(title = "Library") {
                        Box(Modifier.testTag(PILL_TAG).size(40.dp, 24.dp))
                        MastheadAddButton(onAddBooks = {})
                    }
                }
            }
        }

        val container = rule.onNodeWithTag(CONTAINER, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val pill = rule.onNodeWithTag(PILL_TAG, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        val add = rule.onNodeWithContentDescription(ADD_LABEL, useUnmergedTree = true)
            .getUnclippedBoundsInRoot()

        assertNear(8.dp, add.left - pill.right, "8 dp between trailing items")
        assertNear(container.right - 20.dp, add.right, "trailing ends at 20 dp inset")
    }

    private class RecordingHaptics : HapticFeedback {
        val performed = mutableListOf<HapticFeedbackType>()

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            performed += hapticFeedbackType
        }
    }

    private companion object {
        const val CONTAINER = "masthead-container"
        const val PILL_TAG = "masthead-pill-stand-in"
        const val ADD_LABEL = "Add books by upload or barcode scan"

        /** Half a dp of slack, to absorb sub-pixel rounding in the measured bounds. */
        const val TOLERANCE_DP = 0.5

        fun assertNear(expected: Dp, actual: Dp, message: String) {
            assertEquals(
                message,
                expected.value.toDouble(),
                actual.value.toDouble(),
                TOLERANCE_DP,
            )
        }
    }
}
