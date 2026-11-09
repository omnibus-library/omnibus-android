package com.omnibus.omnibus.feature.shell.ui

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class ShellNavigationBarContrastTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // TC-11
    @Test
    fun tc11_contrastScrimIsOffOnlyWhileTheShellIsComposed() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        rule.runOnUiThread { rule.activity.window.isNavigationBarContrastEnforced = true }
        val shown = mutableStateOf(false)
        rule.setContent {
            if (shown.value) DisableNavigationBarScrim()
        }

        rule.runOnIdle { shown.value = true }
        rule.waitForIdle()
        assertFalse(rule.activity.window.isNavigationBarContrastEnforced)

        rule.runOnIdle { shown.value = false }
        rule.waitForIdle()
        assertTrue(rule.activity.window.isNavigationBarContrastEnforced)
    }
}
