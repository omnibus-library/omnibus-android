package com.omnibus.omnibus.feature.audiobook.speed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.Locale

/**
 * The stateless sheet in a [window] (the test host's whole window by default), with its locale set to [locale] (never the
 * process default). The callbacks default to no-ops, so a test passes only the ones it records.
 */
@Composable
internal fun LocalizedSpeedSheetContent(
    locale: Locale,
    uiState: SpeedUIState,
    onPresetChosen: (Double) -> Unit = {},
    onSliderMoved: (Float) -> Unit = {},
    onSliderReleased: () -> Unit = {},
    onSlower: () -> Unit = {},
    onFaster: () -> Unit = {},
    window: Modifier = Modifier.fillMaxSize(),
) {
    val configuration = android.content.res.Configuration(LocalConfiguration.current).apply { setLocale(locale) }
    CompositionLocalProvider(LocalConfiguration provides configuration) {
        OmnibusTheme(darkTheme = true) {
            Box(window) {
                SpeedSheetContent(
                    uiState = uiState,
                    onPresetChosen = onPresetChosen,
                    onSliderMoved = onSliderMoved,
                    onSliderReleased = onSliderReleased,
                    onSlower = onSlower,
                    onFaster = onFaster,
                )
            }
        }
    }
}
