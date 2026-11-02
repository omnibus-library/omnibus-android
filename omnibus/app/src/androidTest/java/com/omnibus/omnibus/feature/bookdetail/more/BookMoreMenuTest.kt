package com.omnibus.omnibus.feature.bookdetail.more

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookMoreMenuTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val rowLabel = "Row one"

    /** Opens the menu and waits until it is on screen. */
    private fun openMenu() {
        rule.onNodeWithTag(BookMoreTestTags.MORE_BUTTON).performClick()
        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()
    }

    /**
     * Taps the page outside the menu with a real pointer event. Compose's own touch injection stays inside the app's
     * window, so a popup never sees it as an outside tap.
     */
    private fun tapOutsideMenu() {
        val page = rule.pageRoot().bounds()
        val origin = IntArray(2)
        rule.activity.findViewById<View>(android.R.id.content).getLocationOnScreen(origin)
        val x = origin[0] + page.left + 4f
        val y = origin[1] + page.bottom - 4f
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, x, y, 0)
        val up = MotionEvent.obtain(SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 50, MotionEvent.ACTION_UP, x, y, 0)
        instrumentation.sendPointerSync(down)
        instrumentation.sendPointerSync(up)
        down.recycle()
        up.recycle()
        rule.waitForIdle()
    }

    /** Waits until the menu has left the composition, after its exit animation. */
    private fun waitForMenuGone() {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithTag(BookMoreTestTags.MENU).fetchSemanticsNodes().isEmpty()
        }
    }

    // TC-3
    @Test
    fun issue116_tc3_tappingMoreOpensTheMenuWithItsRows() {
        rule.setMoreButton(listOf(FakeEntry(rowLabel)))

        openMenu()

        // The menu is a separate window, so its position can't be compared with the button's; the row must sit
        // inside the menu, which is what the menu's own geometry can show.
        rule.onNodeWithText(rowLabel).assertIsDisplayed()
        val menu = rule.onNodeWithTag(BookMoreTestTags.MENU).bounds()
        val row = rule.onNodeWithTag(rowTag(rowLabel)).bounds()
        assertTrue("row ${row.width}px inside menu ${menu.width}px", row.width <= menu.width + 1f)
        assertTrue("row starts inside the menu", row.left >= menu.left - 1f)
    }

    // TC-4
    @Test
    fun issue116_tc4_menuClosesOnRowChoiceBackAndOutsideTap() {
        rule.setMoreButton(listOf(FakeEntry(rowLabel)))

        openMenu()
        rule.onNodeWithText(rowLabel).performClick()
        waitForMenuGone()

        openMenu()
        rule.pressBackKey()
        waitForMenuGone()

        openMenu()
        tapOutsideMenu()
        waitForMenuGone()
    }

    // TC-5
    @Test
    fun issue116_tc5_menuIsAbout276dpWhenItFitsAndNeverWiderThanThePage() {
        rule.setMoreButton(listOf(FakeEntry(rowLabel)))

        openMenu()

        val menu = rule.onNodeWithTag(BookMoreTestTags.MENU).bounds()
        val page = rule.pageRoot().bounds()
        assertTrue("menu ${menu.width}px wider than the page ${page.width}px", menu.width <= page.width + 1f)
        val fullWidth = with(rule.density) { (276 + 32).dp.toPx() }
        if (page.width >= fullWidth) {
            assertEquals(with(rule.density) { 276.dp.toPx() }, menu.width, 1f)
        }
    }

    // TC-13
    @Test
    fun issue116_tc13_entryOverlayIsComposedOutsideTheMenuAndSurvivesItClosing() {
        val sheetOpen = mutableStateOf(false)
        rule.setMoreButton(
            listOf(
                FakeEntry(
                    label = rowLabel,
                    onClick = { sheetOpen.value = true },
                    overlay = { if (sheetOpen.value) Text("sheet open") },
                ),
                FakeEntry(label = null, overlay = { Text("always composed") }),
            ),
        )
        rule.onNodeWithText("always composed").assertIsDisplayed()

        openMenu()
        rule.onNodeWithText(rowLabel).performClick()
        waitForMenuGone()

        rule.onNodeWithText("sheet open").assertIsDisplayed()
    }

    // TC-66
    @Test
    fun issue116_tc66_disabledRowInsideTheMenuDoesNothing() {
        val needsConnection = detailText(R.string.book_detail_more_needs_connection)
        var clicks = 0
        rule.setMoreButton(
            listOf(
                FakeEntry(
                    label = rowLabel,
                    enabled = false,
                    subLabel = needsConnection,
                    onClick = { clicks++ },
                ),
            ),
        )

        openMenu()
        rule.onNodeWithText(needsConnection).assertIsDisplayed()
        rule.onNodeWithTag(rowTag(rowLabel)).performTouchInput { click() }
        rule.waitForIdle()

        rule.onNodeWithTag(BookMoreTestTags.MENU).assertIsDisplayed()
        assertEquals(0, clicks)
    }
}
