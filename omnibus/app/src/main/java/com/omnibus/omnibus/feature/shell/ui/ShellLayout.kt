package com.omnibus.omnibus.feature.shell.ui

import android.graphics.Color
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import com.omnibus.omnibus.ui.components.LocalBottomOverlayInsets
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * Lays [content] out at full window height, with [overlay] drawn over its bottom edge. Entries read the overlay's
 * measured height as `WindowInsets.bottomOverlay`, so they can keep clear of it.
 */
@Composable
internal fun ShellLayout(
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier,
        bottomBar = overlay,
        containerColor = OmnibusTheme.palette.bg0,
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        // The body ignores the padding; the overlay's height is the only thing the entries need from it.
        val bottom = innerPadding.calculateBottomPadding()
        val insets = remember(bottom) { WindowInsets(bottom = bottom) }
        CompositionLocalProvider(LocalBottomOverlayInsets provides insets) { content() }
    }
}

/** Pads an entry clear of the status bar and the bottom overlay, as the shell's other root screens need. */
@Composable
internal fun ShellPaddedEntry(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.bottomOverlay))
            .consumeWindowInsets(WindowInsets.navigationBars),
    ) { content() }
}

/**
 * Pads an entry that draws under the status bar, such as Book Detail, clear of the bottom overlay only. Consuming the
 * navigation bar keeps its own navigation bar padding from adding a second inset.
 */
@Composable
internal fun ShellBookDetailEntry(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.bottomOverlay)
            .consumeWindowInsets(WindowInsets.navigationBars),
    ) { content() }
}

/**
 * Stops the system drawing its own navigation bar scrim while composed. Its translucent band would double the
 * overlay's bottom fade over a 3-button bar (AC8). API 29+ turns off the contrast enforcement, API 26-28 make the
 * bar transparent, and API 24-25 keep the scrim, since they have no light navigation bar icons. The previous value
 * is restored when the shell leaves composition.
 */
@Suppress("DEPRECATION")
@Composable
internal fun DisableNavigationBarScrim() {
    val window = if (LocalInspectionMode.current) null else LocalActivity.current?.window
    if (window == null) return
    DisposableEffect(window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val previous = window.isNavigationBarContrastEnforced
            window.isNavigationBarContrastEnforced = false
            onDispose { window.isNavigationBarContrastEnforced = previous }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val previous = window.navigationBarColor
            window.navigationBarColor = Color.TRANSPARENT
            onDispose { window.navigationBarColor = previous }
        } else {
            onDispose {}
        }
    }
}
