package com.omnibus.omnibus.feature.reader.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.RememberObserver
import androidx.webkit.ScriptHandler
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderAssets
import com.omnibus.omnibus.data.reader.ReaderTheme

/**
 * The [WebView] behind a [ReaderWebView], configured for the reader and tied to the Compose
 * lifetime that remembers it: attached and loading once remembered, destroyed once forgotten.
 *
 * Nothing here draws or handles touches itself. The glue in the page owns swipe, edge taps and the
 * centre tap, so the WebView must receive every touch unmodified.
 *
 * @param onMessage every message the page posts, on the main thread.
 * @param onRebuild the renderer died and this host is no longer usable; the caller should replace
 *   it with a new one, which boots from the controller's last position and settings.
 */
internal class ReaderWebHost(
    context: Context,
    private val controller: ReaderController,
    assetLoaderFactory: ReaderAssetLoaderFactory,
    initialTheme: ReaderTheme,
    initialInsets: PageInsets,
    private val onMessage: (ReaderMessage) -> Unit,
    private val onRebuild: () -> Unit,
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
    private var insets = initialInsets
    private var documentStartScript: ScriptHandler? = null
    private var released = false

    init {
        configure()
        registerDocumentStartScript()
    }

    override fun onRemembered() {
        controller.attach(evaluate)
        webView.loadUrl(ReaderAssets.ENTRY_URL)
    }

    override fun onForgotten() = release()

    override fun onAbandoned() = release()

    /** Repaints the host ground and re-pushes the insets, when either changed. */
    fun applyPageStyle(theme: ReaderTheme, insets: PageInsets) {
        if (released || (theme == this.theme && insets == this.insets)) return
        this.theme = theme
        this.insets = insets
        webView.setBackgroundColor(theme.groundArgb)
        evaluate(ReaderScripts.pageStyle(theme.groundHex, insets))
        registerDocumentStartScript()
    }

    fun onPause() = webView.onPause()

    fun onResume() = webView.onResume()

    private fun release() {
        if (released) return
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
                setOf(ReaderAssets.ORIGIN),
            ) { _, message: WebMessageCompat, _, isMainFrame, _ ->
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
        controller.handle(message)
        onMessage(message)
    }

    private fun registerDocumentStartScript() {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return
        documentStartScript?.remove()
        documentStartScript = WebViewCompat.addDocumentStartJavaScript(
            webView,
            ReaderScripts.pageStyle(theme.groundHex, insets),
            setOf(ReaderAssets.ORIGIN),
        )
    }

    private companion object {
        /** The name `reader.html` posts to: `window.omnibus.postMessage`. */
        const val JS_OBJECT_NAME = "omnibus"
    }
}
