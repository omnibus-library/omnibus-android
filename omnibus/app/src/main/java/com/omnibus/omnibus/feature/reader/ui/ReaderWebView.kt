package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.ui.components.ErrorStateView
import kotlin.math.roundToInt

/**
 * Hosts the EPUB reader page: boots epub.js on the book, shows a loading state until the first
 * page paints, and an error with a retry if the book can't be opened.
 *
 * Nothing interactive is drawn over the WebView. The glue in the page handles swipe, edge taps and
 * the centre tap itself, so any pointer handling layered above would swallow the touches it needs.
 * The chrome belongs around the page, in the bands [ReaderBands] leaves, and the failure state
 * replaces the page rather than covering it.
 *
 * @param bookUuid the book to open, which is also the key its page numbers are cached under.
 * @param settings the typography in force. A change is applied to the page that is showing.
 * @param controller issues page turns and holds the position a reboot returns to.
 * @param onEvent every event the page reports (relocate, toc, selection, chrome toggles), decoded,
 *   and the host's own [ReaderEvent.LoadStatusChanged] each time the load status changes.
 * @param insets pushed into the page as `--inset-*`, so prose never runs under a system bar.
 *   The on-screen keyboard is not one of them: it never opens over the reader, and counting it
 *   would collapse the page.
 */
@Composable
fun ReaderWebView(
    bookUuid: String,
    settings: ReaderSettings,
    controller: ReaderController,
    assetLoaderFactory: ReaderAssetLoaderFactory,
    modifier: Modifier = Modifier,
    onEvent: (ReaderEvent) -> Unit = {},
    insets: WindowInsets = ReaderWebViewDefaults.insets,
) {
    SideEffect { controller.configure(bookUuid, settings) }

    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(controller) {
        snapshotFlow { controller.status }.collect { currentOnEvent(ReaderEvent.LoadStatusChanged(it)) }
    }

    // Bumped to replace the WebView: on retry, and when its renderer dies.
    var generation by remember { mutableIntStateOf(0) }
    val status = controller.status

    Box(modifier.fillMaxSize().background(Color(settings.theme.groundArgb))) {
        if (status is ReaderLoadStatus.Failed) {
            ErrorStateView(
                title = stringResource(R.string.reader_error_title),
                message = stringResource(status.reason.messageRes()),
                actionLabel = stringResource(R.string.reader_try_again),
                onRetry = {
                    controller.reset()
                    generation++
                },
            )
        } else {
            key(generation) {
                ReaderPage(
                    settings = settings,
                    controller = controller,
                    assetLoaderFactory = assetLoaderFactory,
                    insets = insets,
                    onEvent = onEvent,
                    onRebuild = { generation++ },
                )
            }
            LoadingVeil(settings = settings, visible = status is ReaderLoadStatus.Loading)
        }
    }
}

object ReaderWebViewDefaults {
    /** The system bars and display cutout, which the page must keep clear of. */
    val insets: WindowInsets
        @Composable get() = WindowInsets.systemBars.union(WindowInsets.displayCutout)
}

@Composable
private fun ReaderPage(
    settings: ReaderSettings,
    controller: ReaderController,
    assetLoaderFactory: ReaderAssetLoaderFactory,
    insets: WindowInsets,
    onEvent: (ReaderEvent) -> Unit,
    onRebuild: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val pageInsets = PageInsets(
        top = insets.getTop(density).toCssPx(density.density),
        bottom = insets.getBottom(density).toCssPx(density.density),
        left = insets.getLeft(density, layoutDirection).toCssPx(density.density),
        right = insets.getRight(density, layoutDirection).toCssPx(density.density),
    )
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnRebuild by rememberUpdatedState(onRebuild)

    val host = remember {
        ReaderWebHost(
            context = context,
            controller = controller,
            assetLoaderFactory = assetLoaderFactory,
            initialTheme = settings.theme,
            initialInsets = pageInsets,
            onEvent = { currentOnEvent(it) },
            onRebuild = { currentOnRebuild() },
        )
    }

    SideEffect { host.applyPageStyle(settings.theme, pageInsets) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, host) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> host.onPause()
                Lifecycle.Event.ON_RESUME -> host.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(factory = { host.webView }, modifier = Modifier.fillMaxSize())
}

/** Covers the page in its own ground while it loads. Draws only; it never handles input. */
@Composable
private fun LoadingVeil(settings: ReaderSettings, visible: Boolean) {
    val label = stringResource(R.string.reader_loading)
    AnimatedVisibility(visible = visible, exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(settings.theme.groundArgb))
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                color = (if (settings.theme.isLight) Color.Black else Color.White).copy(alpha = 0.45f),
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

/** Raw pixels to CSS px, which are density-independent pixels in an Android WebView. */
private fun Int.toCssPx(density: Float): Int = (this / density).roundToInt()

private fun ReaderLoadFailure.messageRes(): Int =
    when (this) {
        ReaderLoadFailure.Unauthenticated -> R.string.reader_error_session_expired
        ReaderLoadFailure.NotFound -> R.string.reader_error_not_found
        ReaderLoadFailure.NoServer -> R.string.reader_error_no_server
        ReaderLoadFailure.Unreachable -> R.string.reader_error_unreachable
        ReaderLoadFailure.Render -> R.string.reader_error_render
        ReaderLoadFailure.RendererCrashed -> R.string.reader_error_renderer_crashed
    }
