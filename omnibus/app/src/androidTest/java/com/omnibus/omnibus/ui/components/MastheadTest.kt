package com.omnibus.omnibus.ui.components

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import com.omnibus.omnibus.ui.theme.OmnibusTheme
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
}
