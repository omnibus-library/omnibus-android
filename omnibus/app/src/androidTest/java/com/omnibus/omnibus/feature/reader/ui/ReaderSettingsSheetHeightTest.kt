package com.omnibus.omnibus.feature.reader.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * How tall the reader's settings sheet opens. The activity draws edge to edge, as the reader
 * does, so an uncapped sheet would reach the top of the window. The check is relative to the
 * status bar, so it holds on the 320x640 CI AVD and on a phone.
 */
class ReaderSettingsSheetHeightTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun theOpenSheetLeavesPartOfThePageVisibleBelowTheStatusBar() {
        var statusBarBottom = 0.dp
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        rule.setContent {
            val density = LocalDensity.current
            statusBarBottom = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    ReaderSettingsSheet(
                        settings = ReaderSettings(),
                        accent = readerSheetAccent(ReaderBookState.Ready(title = "Test Book"), OmnibusTheme.palette),
                        onSettingsChanged = {},
                        onDismiss = {},
                    )
                }
            }
        }

        // The sheet is the window's only pane, and the pane's bounds follow the sheet as it moves.
        val sheetTop = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.PaneTitle))
            .getBoundsInRoot().top
        assertTrue(
            "the sheet's top ($sheetTop) should sit below the status bar's bottom edge ($statusBarBottom)",
            sheetTop > statusBarBottom,
        )
    }
}
