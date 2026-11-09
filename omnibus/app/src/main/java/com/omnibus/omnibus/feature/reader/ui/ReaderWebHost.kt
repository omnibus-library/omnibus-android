package com.omnibus.omnibus.feature.reader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.FrameLayout
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.RememberObserver
import com.omnibus.omnibus.feature.reader.ui.curl.ChoreographerFrameClock
import com.omnibus.omnibus.feature.reader.ui.curl.CurlEnvironment
import com.omnibus.omnibus.feature.reader.ui.curl.PageCurlHost
import com.omnibus.omnibus.feature.reader.ui.curl.PageTurnFrame
import com.omnibus.omnibus.feature.reader.ui.curl.WebViewSnapshotter
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderAssets
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The origins whose page may post to `window.omnibus`. Only the bundled reader page is trusted, so
 * a book or any other page that reaches the WebView can't drive the reader.
 */
internal val READER_MESSAGE_ORIGINS: Set<String> = setOf(ReaderAssets.ORIGIN)

/**
 * The [WebView] behind a [ReaderWebView], configured for the reader and tied to the Compose
 * lifetime that remembers it: attached and loading once remembered, destroyed once forgotten.
 *
 * The WebView sits in a [PageTurnFrame], which draws the page curl over it. Nothing here handles
 * touches. The glue in the page owns swipe, edge taps and the centre tap, so the WebView must
 * receive every touch unmodified.
 *
 * @param onEvent every event the page reports, decoded, on the main thread. Messages that don't
 *   decode are dropped.
 * @param onRebuild the renderer died and this host is no longer usable; the caller should replace
 *   it with a new one, which boots from the controller's last position and settings.
 * @param curlEnvironment the reduced-motion and low-RAM answers the page turn asks at each turn.
 */
internal class ReaderWebHost(
    context: Context,
    private val controller: ReaderController,
    assetLoaderFactory: ReaderAssetLoaderFactory,
    initialTheme: ReaderTheme,
    initialMargins: ReaderMargins,
    initialInsets: PageInsets,
    private val onEvent: (ReaderEvent) -> Unit,
    private val onRebuild: () -> Unit,
    curlEnvironment: CurlEnvironment,
) : RememberObserver {

    val webView: WebView = WebView(context)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val evaluate: (String) -> Unit = { script ->
        // A WebView only takes calls on the main thread, and the controller can be driven from anywhere.
        if (Looper.myLooper() == Looper.getMainLooper()) {
            webView.evaluateJavascript(script, null)
        } else {
            mainHandler.post { if (!released) webView.evaluateJavascript(script, null) }
        }
    }
    private val assetLoader = assetLoaderFactory.create { failure ->
        // Called on a WebView background thread.
        mainHandler.post { if (!released) controller.onBookFailure(failure) }
    }

    private var theme = initialTheme
    private var margins = initialMargins
    private var insets = initialInsets
    private var documentStartScript: ScriptHandler? = null
    private var released = false

    /** The frame the WebView sits in. The composable hosts this, not the WebView. */
    val frame = PageTurnFrame(context).apply { paper = initialTheme.groundArgb }
    private val curlScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val curlHost = PageCurlHost(
        call = { controller.call(it) },
        evaluate = evaluate,
        snapshotter = WebViewSnapshotter(webView),
        stage = frame,
        clock = ChoreographerFrameClock,
        scope = curlScope,
        environment = curlEnvironment,
    )
    /** The settings the last look was applied with, minus the page turn, which never repaints. */
    private var lastLook: ReaderSettings? = null

    init {
        configure()
        registerDocumentStartScript()
        frame.addView(webView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.host = curlHost
        frame.onSize = curlHost::onSizeChanged
    }

    override fun onRemembered() {
        controller.attach(evaluate)
        webView.loadUrl(ReaderAssets.ENTRY_URL)
    }

    override fun onForgotten() = release()

    override fun onAbandoned() = release()

    /**
     * Applies the page turn and tells the curl when any look setting changed, since a changed look
     * relayouts the page and its snapshots no longer show it. The page turn itself never relayouts.
     */
    fun applySettings(settings: ReaderSettings) {
        if (released) return
        curlHost.mode = settings.pageTurn
        val look = settings.copy(pageTurn = ReaderPageTurn.Slide)
        if (lastLook != null && look != lastLook) curlHost.onRepaint()
        lastLook = look
    }

    /** Repaints the host ground, and re-pushes the insets and the column measure, when any of them changed. */
    fun applyPageStyle(theme: ReaderTheme, insets: PageInsets, margins: ReaderMargins) {
        if (released || (theme == this.theme && insets == this.insets && margins == this.margins)) return
        this.theme = theme
        this.insets = insets
        this.margins = margins
        webView.setBackgroundColor(theme.groundArgb)
        frame.paper = theme.groundArgb
        curlHost.onRepaint()
        evaluate(ReaderScripts.pageStyle(theme.groundHex, insets, margins.css))
        registerDocumentStartScript()
    }

    fun onPause() = webView.onPause()

    fun onResume() = webView.onResume()

    private fun release() {
        if (released) return
        curlHost.release()
        curlScope.cancel()
        // Straight to this page, not through the controller, which may already belong to a replacement.
        evaluate(ReaderScripts.DESTROY)
        released = true
        controller.detach(evaluate)
        documentStartScript?.remove()
        documentStartScript = null
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.removeWebMessageListener(webView, JS_OBJECT_NAME)
        }
        (webView.parent as? ViewGroup)?.removeView(webView)
        (frame.parent as? ViewGroup)?.removeView(frame)
        webView.clearFocus()
        webView.stopLoading()
        webView.webViewClient = WebViewClient()
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        // A WebView sizes its layout viewport from its layout params; without them `height: 100%`
        // in the page can resolve against nothing and collapse the stage.
        webView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        // Before the first paint, so a dark theme never opens on a white frame.
        webView.setBackgroundColor(theme.groundArgb)
        webView.overScrollMode = WebView.OVER_SCROLL_NEVER
        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        // The glue draws its own selection; the platform's text selection menu would fight it.
        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isHapticFeedbackEnabled = false

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assetLoader.shouldInterceptRequest(request.url)

            // The reader is one page; nothing it contains may navigate the WebView away.
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !request.url.toString().startsWith(ReaderAssets.ORIGIN)

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) controller.onPageLoadFailed()
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                if (request.isForMainFrame) controller.onPageLoadFailed()
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // A WebView whose renderer is gone can't be reused, so the caller rebuilds it.
                if (controller.onRendererGone()) onRebuild()
                return true
            }
        }

        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                webView,
                JS_OBJECT_NAME,
                READER_MESSAGE_ORIGINS,
            ){ _, message: WebMessageCompat, _, isMainFrame, _ ->
                // The book's own scripts run in a subframe on this origin and must not be able to
                // impersonate the page.
                if (isMainFrame) message.data?.let(ReaderMessage::parse)?.let(::dispatch)
            }
        } else {
            controller.onPageLoadFailed()
        }
    }

    private fun dispatch(message: ReaderMessage) {
        if (released) return
        val event = ReaderEvent.decode(message) ?: return
        controller.handle(event)
        route(event)
        onEvent(event)
    }

    /** Tells the page turn what the page reports. Every case is listed, so a new event is a compile error here. */
    private fun route(event: ReaderEvent) {
        when (event) {
            ReaderEvent.HostReady -> curlHost.onPageReplaced()
            is ReaderEvent.Status -> if (event.state == "ready") curlHost.onPageReady()
            is ReaderEvent.Relocate -> curlHost.onRelocate(event.data.cfi)
            is ReaderEvent.Selection -> curlHost.onSelection()
            ReaderEvent.SelectionCleared -> curlHost.onSelectionCleared()
            is ReaderEvent.AnnotationTap -> curlHost.onAnnotationTap()
            is ReaderEvent.TurnRequest -> curlHost.tapTurn(event.dir)
            is ReaderEvent.Toc,
            ReaderEvent.ToggleChrome,
            is ReaderEvent.ShareText,
            is ReaderEvent.ShareImage,
            is ReaderEvent.HostReply,
            is ReaderEvent.LoadStatusChanged,
            -> Unit
        }
    }

    private fun registerDocumentStartScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        documentStartScript?.remove()
        documentStartScript = WebViewCompat.addDocumentStartJavaScript(
            webView,
            ReaderScripts.pageStyle(theme.groundHex, insets, margins.css),
            READER_MESSAGE_ORIGINS,
        )
    }

    private companion object {
        /** The name `reader.html` posts to: `window.omnibus.postMessage`. */
        const val JS_OBJECT_NAME = "omnibus"
    }
}
