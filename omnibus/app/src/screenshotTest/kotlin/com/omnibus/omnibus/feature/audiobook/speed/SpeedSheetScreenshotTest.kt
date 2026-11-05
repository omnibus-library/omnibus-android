package com.omnibus.omnibus.feature.audiobook.speed

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import java.util.Locale

/** The title with the rate on its row, the eight presets in two rows of four with 1.0× selected, the slider and steps. */
@PreviewTest
@PreviewLightDark
@Composable
fun SpeedSheetAtOneTimes() {
    // The sheet formats numbers in the composition's locale, so pin it rather than take the host's.
    val configuration = Configuration(LocalConfiguration.current).apply { setLocale(Locale.US) }
    CompositionLocalProvider(LocalConfiguration provides configuration) {
        Shot {
            SpeedSheetContent(
                uiState = SpeedUIState.forRate(1.0),
                onPresetChosen = {},
                onSliderMoved = {},
                onSliderReleased = {},
                onSlower = {},
                onFaster = {},
            )
        }
    }
}
