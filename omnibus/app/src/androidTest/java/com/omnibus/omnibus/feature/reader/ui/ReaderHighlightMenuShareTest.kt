package com.omnibus.omnibus.feature.reader.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.HighlightMenu
import com.omnibus.omnibus.feature.reader.HighlightMenuAction
import com.omnibus.omnibus.feature.reader.HighlightMenuEntry
import com.omnibus.omnibus.feature.reader.highlightMenuActions
import com.omnibus.omnibus.feature.reader.menuTarget
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Share in the reader's menus (#330): the button the enum puts on each menu's action row, and the share sheet its
 * effect starts. Every highlight is a fraction of the window, so the cases hold on the 320x640 AVD and on a phone.
 */
class ReaderHighlightMenuShareTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun window(): Pair<Float, Float> {
        val root = rule.onRoot().getUnclippedBoundsInRoot()
        return (root.right - root.left).value to (root.bottom - root.top).value
    }

    /** One highlight with [TEXT] on the line at 40% of the window's height. */
    private fun highlight(width: Float, height: Float): HighlightMenuEntry = HighlightMenuEntry(
        cfiRange = "epubcfi(/6/2!/4/2/1:0,/4/2/1:9)",
        rects = listOf(
            PageRect(
                x = (width * 0.2f).toDouble(),
                y = (height * 0.4f).toDouble(),
                width = (width * 0.6f).toDouble(),
                height = (height * 0.05f).toDouble(),
            ),
        ),
        color = HighlightColor.Amber,
        text = TEXT,
        hasNote = false,
    )

    /** A settled one-line selection of [TEXT] in the middle of the window. */
    private fun selection(width: Float, height: Float): SelectionData {
        val line = (height * 0.05f).toDouble()
        val top = (height * 0.5f).toDouble()
        val left = (width * 0.2f).toDouble()
        val right = (width * 0.8f).toDouble()
        return SelectionData(
            cfiRange = "epubcfi(/6/2!/4/2/1:0,/4/2/1:9)",
            text = TEXT,
            rects = listOf(PageRect(x = left, y = top, width = right - left, height = line)),
            start = SelectionHandle(x = left, y = top, height = line),
            end = SelectionHandle(x = right, y = top, height = line),
        )
    }

    /** Sets the tap menu over the highlight [menu] holds, with the actions the enum offers it. */
    private fun setHighlightMenu(menu: MutableState<HighlightMenu?>, onAction: (HighlightMenuAction) -> Unit = {}) {
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    menu.value?.let { current ->
                        ReaderHighlightMenuOverlay(
                            menu = current,
                            actions = highlightMenuActions(current.activeEntry.menuTarget),
                            theme = ReaderTheme.Light,
                            onStep = {},
                            onColor = {},
                            onAction = onAction,
                            onDismiss = {},
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val (width, height) = window()
        rule.runOnIdle { menu.value = HighlightMenu(listOf(highlight(width, height))) }
        rule.waitForIdle()
    }

    // TC-8 (AC1, AC6)
    @Test
    fun issue330_tc8_theTapMenusShareButtonCallsOnActionWithShare() {
        val menu = mutableStateOf<HighlightMenu?>(null)
        val actions = mutableListOf<HighlightMenuAction>()
        setHighlightMenu(menu, onAction = { actions += it })

        rule.onNodeWithContentDescription("Share").performClick()
        rule.waitForIdle()

        assertEquals(listOf(HighlightMenuAction.Share), actions)
    }

    // TC-9 (AC1)
    @Test
    fun issue330_tc9_theTapMenuDrawsShareLeftOfCopy() {
        setHighlightMenu(mutableStateOf(null))

        val share = rule.onNodeWithContentDescription("Share").getUnclippedBoundsInRoot()
        val copy = rule.onNodeWithContentDescription("Copy").getUnclippedBoundsInRoot()

        assertTrue("Share should end before Copy starts", share.right <= copy.left)
    }

    // TC-10 (AC6)
    @Test
    fun issue330_tc10_longPressOnShareShowsItsTooltip() {
        setHighlightMenu(mutableStateOf(null))

        rule.onNodeWithContentDescription("Share").performTouchInput { longClick() }

        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithText("Share").fetchSemanticsNodes().isNotEmpty() }
    }

    // TC-11 (AC1)
    @Test
    fun issue330_tc11_theSelectionMenusShareButtonCallsOnActionWithShare() {
        val shown = mutableStateOf<SelectionData?>(null)
        val actions = mutableListOf<HighlightMenuAction>()
        rule.setContent {
            OmnibusTheme {
                Box(Modifier.fillMaxSize()) {
                    shown.value?.let { current ->
                        ReaderSelectionOverlay(
                            selection = current,
                            menuShown = true,
                            theme = ReaderTheme.Light,
                            onGrab = {},
                            onDrag = { _, _, _ -> },
                            onRelease = {},
                            actions = highlightMenuActions(current.menuTarget),
                            onColorChosen = {},
                            onAction = { actions += it },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        val (width, height) = window()
        rule.runOnIdle { shown.value = selection(width, height) }
        rule.waitForIdle()

        rule.onNodeWithContentDescription("Share").performClick()
        rule.waitForIdle()

        assertEquals(listOf(HighlightMenuAction.Share), actions)
    }

    // TC-12 (AC2)
    @Test
    fun issue330_tc12_shareStartsTheShareSheetWithTheCollapsedPassageAsPlainText() {
        val started = runShareEffect(passage = "  It was\n\n the   best \t of times.  ")

        val chooser = started.single()
        @Suppress("DEPRECATION")
        val inner = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        assertEquals("It was the best of times.", inner.getStringExtra(Intent.EXTRA_TEXT))
    }

    // TC-13 (AC5)
    @Test
    fun issue330_tc13_shareWithNoPassageStartsNothing() {
        val started = runShareEffect(passage = null)

        assertEquals(emptyList<Intent>(), started)
    }

    /**
     * Runs the menu's effect for Share on [passage] once, under a context that records the activities it is asked to
     * start instead of starting them, and returns what it recorded.
     */
    private fun runShareEffect(passage: String?): List<Intent> {
        val started = mutableListOf<Intent>()
        var ran = false
        rule.setContent {
            val recording = RecordingContext(LocalContext.current, started)
            CompositionLocalProvider(LocalContext provides recording) {
                val effects = rememberMenuActionEffects()
                SideEffect {
                    if (!ran) {
                        ran = true
                        effects(HighlightMenuAction.Share, passage)
                    }
                }
            }
        }
        rule.waitForIdle()
        assertTrue("the effect should have run", ran)
        return started
    }

    /** A context that records [started] activities instead of starting them. */
    private class RecordingContext(base: Context, private val started: MutableList<Intent>) : ContextWrapper(base) {
        override fun startActivity(intent: Intent) {
            started += intent
        }
    }

    private companion object {
        const val TEXT = "Somewhere behind the wall"
    }
}
