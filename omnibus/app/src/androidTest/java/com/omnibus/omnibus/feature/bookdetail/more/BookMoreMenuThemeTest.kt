package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.feature.bookdetail.menuPanelColor
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.contrastRatio
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** WCAG AA contrast for body text. */
private const val READABLE_CONTRAST = 4.5

private const val ROW_LABEL = "Alpha row"

class BookMoreMenuThemeTest {

    @get:Rule
    val rule = createComposeRule()

    /** Sets an open More menu with one row, in the theme [dark] reads at each recomposition. */
    private fun setMenu(dark: () -> Boolean) {
        rule.setContent {
            OmnibusTheme(darkTheme = dark()) {
                Box(Modifier.fillMaxSize().testTag(MORE_HOST)) {
                    BookMoreMenu(
                        expanded = true,
                        onDismiss = {},
                        context = testSectionContext(),
                        entries = listOf(TestEntry(ROW_LABEL)),
                    )
                }
            }
        }
    }

    /** The contrast between the row label's ink in [palette] and the panel behind the row. */
    private fun labelContrast(palette: OmnibusPalette): Double =
        contrastRatio(rule.onNodeWithTag(rowTag(ROW_LABEL)).menuPanelColor(), palette.ink0)

    @Test
    fun issue327_lightTheme_labelsReadOnTheMenuPanel() {
        setMenu { false }

        val contrast = labelContrast(OmnibusPalette.Light)

        assertTrue("contrast was $contrast", contrast >= READABLE_CONTRAST)
    }

    @Test
    fun issue327_darkTheme_labelsReadOnTheMenuPanel() {
        setMenu { true }

        val contrast = labelContrast(OmnibusPalette.Atrium)

        assertTrue("contrast was $contrast", contrast >= READABLE_CONTRAST)
    }

    @Test
    fun issue327_themeChangedToLightWhileOpen_labelsReadOnTheMenuPanel() {
        val dark = mutableStateOf(true)
        setMenu { dark.value }
        rule.onNodeWithTag(rowTag(ROW_LABEL)).assertIsDisplayed()

        dark.value = false
        rule.waitForIdle()
        val contrast = labelContrast(OmnibusPalette.Light)

        assertTrue("contrast was $contrast", contrast >= READABLE_CONTRAST)
    }
}
