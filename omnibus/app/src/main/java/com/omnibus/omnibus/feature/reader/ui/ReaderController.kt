package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderSettings

/** Where a [ReaderController]'s book is in loading. */
sealed interface ReaderLoadStatus {
    /** The page is booting or the book is opening. */
    data object Loading : ReaderLoadStatus

    /** The first page has painted. */
    data object Ready : ReaderLoadStatus

    data class Failed(val reason: ReaderLoadFailure) : ReaderLoadStatus
}

/** Why a book didn't open. */
enum class ReaderLoadFailure {
    /** The server rejected the saved sign-in. */
    Unauthenticated,

    /** The server has no file for this book. */
    NotFound,

    /** No server address is saved. */
    NoServer,

    /** The server couldn't be reached or answered unexpectedly. */
    Unreachable,

    /** epub.js couldn't open or render the book, or the reader page didn't load. */
    Render,

    /** The WebView's renderer kept dying. */
    RendererCrashed,
}

/**
 * Drives one [ReaderWebView]: page turns and navigation in, load status and the last reading
 * position out.
 *
 * It also holds what a reboot needs. The page can be replaced under the reader (its renderer
 * died, or the activity was recreated on rotation), and the new one is booted at [restoreCfi] in
 * whatever settings are in force at that moment, not where and how the book first opened.
 *
 * Commands do nothing until a page is attached and ready.
 */
class ReaderController(startCfi: String? = null) {

    /** Observable, so the screen can show a loading or error state. */
    var status: ReaderLoadStatus by mutableStateOf(ReaderLoadStatus.Loading)
        private set

    /**
     * The last position the reader was actually shown at, or the position it opened at. Echoes
     * don't move it: a rotation's corrected re-pagination can be a page short of the one the reader
     * was on, and rebooting there would lose their place.
     */
    var restoreCfi: String? = startCfi
        private set

    private var evaluator: ((String) -> Unit)? = null
    private var bookUuid: String? = null
    private var settings = ReaderSettings()

    /** The typography the page was last handed, or null when no page is holding ours. */
    private var appliedSettings: ReaderSettings? = null
    private var isReady = false
    private var consecutiveRendererLosses = 0

    fun next() = run(ReaderScripts.NEXT)

    fun previous() = run(ReaderScripts.PREVIOUS)

    /** Navigates to a contents entry's href or to a CFI. */
    fun display(target: String) = run(ReaderScripts.display(target))

    /** Tells the page to release the book. Called as the reader goes away. */
    fun teardown() {
        run(ReaderScripts.DESTROY)
        appliedSettings = null
    }

    // Everything below is the host's side of the conversation, driven by ReaderWebView.

    internal fun attach(evaluate: (String) -> Unit) {
        evaluator = evaluate
    }

    /** Detaches [evaluate] unless a newer page has already taken over. */
    internal fun detach(evaluate: (String) -> Unit) {
        if (evaluator !== evaluate) return
        evaluator = null
        appliedSettings = null
        isReady = false
    }

    /** Records the book and the settings in force, applying a change to a page that is showing. */
    internal fun configure(bookUuid: String, settings: ReaderSettings) {
        this.bookUuid = bookUuid
        this.settings = settings
        syncSettings()
    }

    /** Starts a fresh load after a failure or a replaced page. */
    internal fun reset() {
        isReady = false
        appliedSettings = null
        status = ReaderLoadStatus.Loading
    }

    /** Applies the events the controller acts on. Every case is listed, so a new event is a compile error here. */
    internal fun handle(event: ReaderEvent) {
        when (event) {
            ReaderEvent.HostReady -> onHostReady()
            is ReaderEvent.Status -> when (event.state) {
                "ready" -> onReady()
                "error" -> fail(ReaderLoadFailure.Render)
            }
            is ReaderEvent.Relocate -> onRelocate(event.data)
            is ReaderEvent.Toc,
            is ReaderEvent.Selection,
            ReaderEvent.SelectionCleared,
            is ReaderEvent.AnnotationTap,
            ReaderEvent.ToggleChrome,
            is ReaderEvent.ShareText,
            is ReaderEvent.ShareImage,
            is ReaderEvent.TurnRequest,
            is ReaderEvent.LoadStatusChanged,
            -> Unit
        }
    }

    internal fun onBookFailure(failure: BookFileResult.Failure) {
        fail(
            when (failure) {
                BookFileResult.Unauthenticated -> ReaderLoadFailure.Unauthenticated
                BookFileResult.NotFound -> ReaderLoadFailure.NotFound
                BookFileResult.NoServer -> ReaderLoadFailure.NoServer
                BookFileResult.Error -> ReaderLoadFailure.Unreachable
            },
        )
    }

    internal fun onPageLoadFailed() = fail(ReaderLoadFailure.Render)

    /**
     * The WebView's renderer went away. Returns true when the page should be rebuilt and rebooted,
     * false when it has died too many times in a row without reaching a first page to be worth
     * another try.
     */
    internal fun onRendererGone(): Boolean {
        isReady = false
        appliedSettings = null
        if (++consecutiveRendererLosses > MAX_RENDERER_LOSSES) {
            status = ReaderLoadStatus.Failed(ReaderLoadFailure.RendererCrashed)
            return false
        }
        status = ReaderLoadStatus.Loading
        return true
    }

    /**
     * Announced on every load of `reader.html`, so a second one means the page was replaced under
     * the reader.
     */
    private fun onHostReady() {
        isReady = false
        if (status is ReaderLoadStatus.Ready) status = ReaderLoadStatus.Loading
        val uuid = bookUuid ?: return
        val evaluate = evaluator ?: return
        evaluate(ReaderScripts.boot(uuid, settings, restoreCfi))
        // The boot baked these settings in, so a change before first paint is a diff against them.
        appliedSettings = settings
    }

    private fun onReady() {
        isReady = true
        consecutiveRendererLosses = 0
        status = ReaderLoadStatus.Ready
        // A setting changed between the boot snapshot and first paint reaches the page here, or
        // not until the next reboot.
        syncSettings()
    }

    private fun onRelocate(data: RelocateData) {
        if (!data.isMovement) return
        data.cfi?.takeIf { it.isNotBlank() }?.let { restoreCfi = it }
    }

    private fun fail(reason: ReaderLoadFailure) {
        if (isReady) return
        val current = status
        // A generic render error must not hide the more specific reason it came from.
        if (current is ReaderLoadStatus.Failed && reason == ReaderLoadFailure.Render) return
        status = ReaderLoadStatus.Failed(reason)
    }

    private fun syncSettings() {
        val shown = appliedSettings ?: return
        if (!isReady || shown == settings) return
        ReaderScripts.settingsChange(shown, settings).forEach(::run)
        appliedSettings = settings
    }

    private fun run(script: String) {
        evaluator?.invoke(script)
    }

    private companion object {
        const val MAX_RENDERER_LOSSES = 3
    }
}

/**
 * Remembers a [ReaderController] across recomposition and activity recreation.
 *
 * @param startCfi where a fresh controller opens the book, or null for the beginning.
 */
@Composable
fun rememberReaderController(startCfi: String? = null): ReaderController =
    rememberSaveable(saver = ReaderControllerSaver) { ReaderController(startCfi) }

/** Saves the position across activity recreation, so a rotation reboots at the same page. */
private val ReaderControllerSaver: Saver<ReaderController, Any> =
    listSaver(
        save = { listOf(it.restoreCfi ?: "") },
        restore = { ReaderController(startCfi = it[0].ifEmpty { null }) },
    )
