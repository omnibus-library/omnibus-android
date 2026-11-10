package com.omnibus.omnibus.feature.shell.ui

import android.graphics.Color
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.layout.WindowInfoTracker
import com.omnibus.omnibus.feature.audiobook.mini.MiniPlayerFloatPlacement
import com.omnibus.omnibus.feature.shell.ShellNavigationLayout
import com.omnibus.omnibus.feature.shell.isTabletop
import com.omnibus.omnibus.feature.shell.shellNavigationLayout
import com.omnibus.omnibus.ui.components.LocalBottomOverlayInsets
import com.omnibus.omnibus.ui.components.bottomOverlay
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/**
 * Lays [content] out at full window height, with [overlay] drawn over its bottom edge. Entries read the overlay's
 * measured height as `WindowInsets.bottomOverlay`, so they can keep clear of it.
 *
 * In [ShellNavigationLayout.Rail], [rail] sits on the leading edge and the content is laid out in the width beside it,
 * not under it. Both layouts share one tree, so [content] keeps its composition when the layout changes.
 */
@Composable
internal fun ShellLayout(
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationLayout: ShellNavigationLayout = ShellNavigationLayout.Bar,
    rail: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val isRail = navigationLayout == ShellNavigationLayout.Rail
    Row(modifier) {
        if (isRail) rail()
        Scaffold(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .then(if (isRail) Modifier.consumeWindowInsets(RailStartInsets) else Modifier),
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
}

/**
 * The start-side insets the rail already clears. The rail's content consumes them, so the content beside it doesn't
 * pad them a second time.
 */
private val RailStartInsets: WindowInsets
    @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Start)

/**
 * The shell's bottom overlay: the floating mini player above the tab bar ([ShellNavigationLayout.Bar]) or above a spacer
 * as tall as the navigation bar ([ShellNavigationLayout.Rail]), so the overlay is never shorter than the navigation bar.
 * [miniPlayer] is always the first call, so it keeps its composition when the layout changes.
 */
@Composable
internal fun ShellBottomOverlay(
    navigationLayout: ShellNavigationLayout,
    miniPlayer: @Composable (MiniPlayerFloatPlacement) -> Unit,
    tabBar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val placement = when (navigationLayout) {
        ShellNavigationLayout.Bar -> MiniPlayerFloatPlacement.Bar
        ShellNavigationLayout.Rail -> MiniPlayerFloatPlacement.Rail
    }
    Column(modifier.fillMaxWidth()) {
        miniPlayer(placement)
        if (navigationLayout == ShellNavigationLayout.Bar) {
            tabBar()
        } else {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/**
 * The layout for the current window: the rail from 600dp wide, the bar below it and whenever a fold is in tabletop
 * posture. Previews and screenshot tests have no activity, so they read as not tabletop.
 */
@Composable
internal fun rememberShellNavigationLayout(): ShellNavigationLayout {
    val width = LocalWindowInfo.current.containerDpSize.width
    val activity = if (LocalInspectionMode.current) null else LocalActivity.current
    val tabletop by produceState(false, activity) {
        if (activity != null) {
            WindowInfoTracker.getOrCreate(activity).windowLayoutInfo(activity).collect { value = isTabletop(it.displayFeatures) }
        }
    }
    return shellNavigationLayout(width, tabletop)
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
 * Pads an entry that draws under the status bar, such as Book Detail or a screen with its own top app bar, clear of the
 * bottom overlay only. Consuming the navigation bar keeps its own navigation bar padding from adding a second inset.
 */
@Composable
internal fun ShellUnderStatusBarEntry(content: @Composable () -> Unit) {
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
