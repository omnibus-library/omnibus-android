package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/** The bottom-only insets of the overlay a host draws over the screen's bottom edge. Null outside a host. */
val LocalBottomOverlayInsets: ProvidableCompositionLocal<WindowInsets?> = compositionLocalOf { null }

/**
 * The bottom obstruction of whatever the host draws over the screen's bottom edge: the shell's mini player and tab
 * bar, which include the navigation bar. Outside a host it is the navigation bar. Padding by it also consumes it.
 */
val WindowInsets.Companion.bottomOverlay: WindowInsets
    @Composable get() = LocalBottomOverlayInsets.current ?: WindowInsets.navigationBars
