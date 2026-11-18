package com.omnibus.omnibus.feature.reader.ui

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderOpening
import com.omnibus.omnibus.feature.reader.ReaderStart
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The selection layer's tint, handles and menu, over a settled selection built from the window's own size, and the
 * wash over the page's glue rects (TC-36). Every position is a fraction of the window, so the cases hold on the 320x640 AVD.
 */
class ReaderSelectionLayerTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val epub = TestEpub.build()

    private val density: Float get() = rule.activity.resources.displayMetrics.density

    /** The window's size in dp, read from the root, so every rect in a test is a fraction of it. */
    private fun window(): Pair<Float, Float> {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        return (root.right - root.left).value to (root.bottom - root.top).value
    }

    /**
     * A settled three-line selection in the upper half of a [width] by [height] dp window. Its menu has room above it,
     * on a phone-sized window, so the menu is drawn above.
     */
    private fun settledSelection(width: Float, height: Float): SelectionData {
        val line = (height * 0.05f).toDouble()
        val top = (height * 0.5f).toDouble()
        val left = (width * 0.2f).toDouble()
        val right = (width * 0.8f).toDouble()
        return SelectionData(
            cfiRange = "epubcfi(/6/2!/4/2/1:0,/4/2/1:9)",
            text = "Somewhere behind the wall",
            rects = listOf(
                PageRect(x = left, y = top, width = right - left, height = line),
                PageRect(x = left, y = top + line, width = right - left, height = line),
                PageRect(x = left, y = top + 2 * line, width = right - left, height = line),
            ),
            start = SelectionHandle(x = left, y = top, height = line),
            end = SelectionHandle(x = right, y = top + 2 * line, height = line),
        )
    }

    /** Sets the overlay over [selection], drawn over [under] so a test can see which taps get past it. */
    private fun setOverlay(
        selection: MutableState<SelectionData?>,
        menuShown: Boolean = false,
        under: @Composable () -> Unit = {},
        onGrab: (SelectionEdge) -> Unit = {},
        onDrag: (Float, Float, Float) -> Unit = { _, _, _ -> },
        onRelease: () -> Unit = {},
        onColorChosen: (HighlightColor) -> Unit = {},
        onCopy: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    under()
                    selection.value?.let { current ->
                        ReaderSelectionOverlay(
                            selection = current,
                            menuShown = menuShown,
                            theme = ReaderTheme.Light,
                            onGrab = onGrab,
                            onDrag = onDrag,
                            onRelease = onRelease,
                            onColorChosen = onColorChosen,
                            onCopy = onCopy,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    /** Shows [settled] on the overlay once the window has been laid out, so its rects are fractions of the window. */
    private fun showSettled(selection: MutableState<SelectionData?>) {
        rule.waitForIdle()
        val (width, height) = window()
        rule.runOnIdle { selection.value = settledSelection(width, height) }
        rule.waitForIdle()
    }

    /** The centre of a handle's touch box in the root's px. */
    private fun centreOf(tag: String): Offset =
        rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

    private val swatchNames = listOf("Amber highlight", "Green highlight", "Blue highlight", "Rose highlight", "Violet highlight")

    // TC-31
    @Test
    fun issue54_tc31_theSixMenuControlsAreTheOnlyNodesWithAnActionOrAName() {
        val selection = mutableStateOf<SelectionData?>(null)
        setOverlay(selection, menuShown = true)
        showSettled(selection)

        val named = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        assertEquals(6, rule.onAllNodes(hasClickAction().or(named)).fetchSemanticsNodes().size)
    }

    // TC-31
    @Test
    fun issue54_tc31_theColoursAreButtonsLeftToRightWithCopyBelowThem() {
        val selection = mutableStateOf<SelectionData?>(null)
        setOverlay(selection, menuShown = true)
        showSettled(selection)

        val swatches = swatchNames.map { rule.onNodeWithContentDescription(it) }
        swatches.forEach { it.assertHasClickAction() }
        swatches.forEach { it.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)) }
        val lefts = swatches.map { it.getUnclippedBoundsInRoot().left.value }
        assertEquals("the colours should run left to right", lefts.sorted(), lefts)

        val copy = rule.onNodeWithText("Copy")
        copy.assertHasClickAction()
        val copyTop = copy.getUnclippedBoundsInRoot().top.value
        swatches.forEach { swatch ->
            assertTrue("Copy should sit below every colour", copyTop > swatch.getUnclippedBoundsInRoot().bottom.value)
        }
    }

    // TC-32
    @Test
    fun issue54_tc32_tappingEachSwatchChoosesItsColourInOrder() {
        val selection = mutableStateOf<SelectionData?>(null)
        val chosen = mutableListOf<HighlightColor>()
        setOverlay(selection, menuShown = true, onColorChosen = { chosen += it })
        showSettled(selection)

        swatchNames.forEach { rule.onNodeWithContentDescription(it).performClick() }
        rule.waitForIdle()

        assertEquals(
            listOf(HighlightColor.Amber, HighlightColor.Green, HighlightColor.Blue, HighlightColor.Rose, HighlightColor.Violet),
            chosen,
        )
    }

    // TC-32
    @Test
    fun issue54_tc32_tappingCopyCallsOnCopyOnce() {
        val selection = mutableStateOf<SelectionData?>(null)
        var copies = 0
        setOverlay(selection, menuShown = true, onCopy = { copies++ })
        showSettled(selection)

        rule.onNodeWithText("Copy").performClick()
        rule.waitForIdle()

        assertEquals(1, copies)
    }

    // TC-33
    @Test
    fun issue54_tc33_draggingTheEndHandleMovesItsCaretByTheFingerTravel() {
        val selection = mutableStateOf<SelectionData?>(null)
        val grabbed = mutableListOf<SelectionEdge>()
        val drags = mutableListOf<Triple<Float, Float, Float>>()
        val releases = mutableListOf<Unit>()
        setOverlay(
            selection,
            onGrab = { grabbed += it },
            onDrag = { caretX, caretY, fingerX -> drags += Triple(caretX, caretY, fingerX) },
            onRelease = { releases += Unit },
        )
        showSettled(selection)
        val settled = selection.value!!
        val from = centreOf(ReaderSelectionTags.END_HANDLE)
        val travel = 0.25f * window().first * density

        rule.onRoot().performTouchInput {
            down(from)
            moveBy(Offset(travel / 2, 0f))
            moveBy(Offset(travel / 2, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals(listOf(SelectionEdge.End), grabbed)
        assertTrue("the drag should report the caret", drags.isNotEmpty())
        val (caretX, _, fingerX) = drags.last()
        val travelDp = (travel / density).toDouble()
        assertEquals(
            "the caret should move by the finger's travel",
            travelDp,
            (caretX - settled.end!!.x).toDouble(),
            1.0,
        )
        assertEquals(
            "the finger should stay the same distance from the caret",
            (drags.first().third - drags.first().first).toDouble(),
            (fingerX - caretX).toDouble(),
            1.0,
        )
        assertEquals("the release should fire once, when the finger lifts", 1, releases.size)
    }

    // TC-34
    @Test
    fun issue54_tc34_aHeldEndHandleKeepsDraggingWhileItsCaretIsOffThePage() {
        val selection = mutableStateOf<SelectionData?>(null)
        val drags = mutableListOf<Triple<Float, Float, Float>>()
        val releases = mutableListOf<Unit>()
        setOverlay(
            selection,
            onDrag = { caretX, caretY, fingerX -> drags += Triple(caretX, caretY, fingerX) },
            onRelease = { releases += Unit },
        )
        showSettled(selection)
        val settled = selection.value!!
        val from = centreOf(ReaderSelectionTags.END_HANDLE)

        rule.onRoot().performTouchInput {
            down(from)
            moveBy(Offset(0f, 40f * density))
        }
        rule.waitForIdle()
        assertTrue("the drag should be under way", drags.isNotEmpty())

        rule.runOnIdle { selection.value = settled.copy(end = null) }
        rule.waitForIdle()
        val beforeMove = drags.size
        rule.onRoot().performTouchInput { moveBy(Offset(0f, 20f * density)) }
        rule.waitForIdle()
        assertTrue("drags should keep arriving while the end's caret is off the page", drags.size > beforeMove)

        rule.runOnIdle { selection.value = null }
        rule.waitForIdle()
        assertEquals("the layer leaving should release the held handle once", 1, releases.size)
        rule.onRoot().performTouchInput { up() }
    }

    // TC-35
    @Test
    fun issue54_tc35_aTapAwayFromTheHandlesAndMenuReachesThePage() {
        val selection = mutableStateOf<SelectionData?>(null)
        val taps = mutableListOf<Unit>()
        setOverlay(
            selection,
            menuShown = true,
            under = { Box(Modifier.fillMaxSize().clickable { taps += Unit }) },
        )
        showSettled(selection)
        val size = rule.onRoot().fetchSemanticsNode().size

        rule.onRoot().performTouchInput { click(Offset(size.width * 0.5f, size.height * 0.9f)) }
        rule.waitForIdle()

        assertEquals(1, taps.size)
    }

    // TC-36
    @Test
    fun issue54_tc36_theWashDrawsOverTheWordTheGlueSelected() {
        val controller = ReaderController()
        val ui = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Test Book"),
                opening = ReaderOpening.Resolved(ReaderStart.Beginning),
                settingsLoaded = true,
            ),
        )
        val assetLoaderFactory = ReaderAssetLoaderFactory(
            context,
            Dispatchers.Unconfined,
            BookFileSource { BookFileResult.Opened(ByteArrayInputStream(epub), epub.size.toLong()) },
        )
        rule.setContent {
            OmnibusTheme {
                ReaderScreenContent(
                    uiState = ui.value,
                    bookUuid = "selection-wash",
                    controller = controller,
                    assetLoaderFactory = assetLoaderFactory,
                    commands = emptyFlow(),
                    onPageEvent = { event ->
                        when (event) {
                            is ReaderEvent.LoadStatusChanged -> ui.value = ui.value.copy(loadStatus = event.status)
                            is ReaderEvent.Selection -> ui.value = ui.value.copy(selection = event.data)
                            else -> Unit
                        }
                    },
                    onSheetDismissed = {},
                    onTocEntryChosen = {},
                    onMenuOpened = {},
                    onMenuDismissed = {},
                    onMenuRowChosen = {},
                    onRetryBookRequested = {},
                    onReaderClosed = {},
                    onReaderStopped = {},
                    onSyncOfferAccepted = {},
                    onSyncOfferDismissed = {},
                    onClose = {},
                )
            }
        }
        rule.waitUntil(timeoutMillis = 30_000) { ui.value.loadStatus == ReaderLoadStatus.Ready }

        val word = call(controller, FIRST_WORD_POINT).jsonObject
        call(controller, "OmnibusReader.beginSelectionAt(${word.number("x")}, ${word.number("y")})")
        rule.waitUntil(timeoutMillis = 15_000) { ui.value.selection?.rects?.isNotEmpty() == true }
        val rect = ui.value.selection!!.rects.first()

        val webView = rule.runOnUiThread { findWebView() }!!
        val origin = IntArray(2)
        rule.runOnUiThread { webView.getLocationInWindow(origin) }
        val wash = rule.onAllNodesWithTag(ReaderSelectionTags.WASH, useUnmergedTree = true).fetchSemanticsNodes().first()
        val bounds = wash.boundsInWindow
        val tolerance = density.toDouble()
        assertEquals("the wash should start one px-inset left of the glue's rect", origin[0] + (rect.x - 1) * density, bounds.left.toDouble(), tolerance)
        assertEquals("the wash should start half a px above the glue's rect", origin[1] + (rect.y - 0.5) * density, bounds.top.toDouble(), tolerance)
        assertEquals("the wash should be the glue's width plus two px", (rect.width + 2) * density, bounds.width.toDouble(), tolerance)
    }

    private fun findWebView(view: View = rule.activity.window.decorView): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findWebView(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** Awaits [expr] on the page. [ReaderController.call] must run on the main thread, where its replies arrive. */
    private fun call(controller: ReaderController, expr: String): JsonElement =
        runBlocking(Dispatchers.Main) { controller.call(expr) } ?: error("the page did not answer: $expr")

    private fun JsonObject.number(key: String): Double = getValue(key).jsonPrimitive.double

    private companion object {
        /** The host-window point at the middle of the first line of the first paragraph, a few px into its first word. */
        const val FIRST_WORD_POINT =
            "(function(){var f=document.querySelector('iframe');var o=f.getBoundingClientRect();" +
                "var r=f.contentDocument.querySelector('p').getBoundingClientRect();" +
                "return {x:o.left+r.left+12,y:o.top+r.top+r.height/2};})()"
    }
}
