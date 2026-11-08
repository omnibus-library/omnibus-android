package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/** That each page's copy can be reached. UnderConstructionScreenshotTest covers how the pages look. */
class UnderConstructionScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun assertMessageReachable(page: UnderConstructionPage) {
        rule.setContent {
            OmnibusTheme {
                UnderConstructionScreen(page)
            }
        }

        val message = InstrumentationRegistry.getInstrumentation().targetContext.getString(page.message)
        rule.onNodeWithText(message).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun searchPageMessageIsReachable() = assertMessageReachable(UnderConstructionPage.Search)

    @Test
    fun statsPageMessageIsReachable() = assertMessageReachable(UnderConstructionPage.Stats)

    @Test
    fun youPageMessageIsReachable() = assertMessageReachable(UnderConstructionPage.You)
}
