package com.omnibus.omnibus.feature.bookdetail.more

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.feature.bookdetail.more.shelves.ManualShelfRow
import com.omnibus.omnibus.feature.bookdetail.more.shelves.SmartShelfRow
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** Tag of the box that hosts a More menu under test, so a test can find the screen's root. */
const val MORE_HOST = "more-test-host"

/**
 * A More menu entry for tests. [label] null emits no rows. A row runs [onClick] after closing the menu, like a real
 * entry that opens something. [overlay] is composed beside the disc.
 */
class TestEntry(
    private val label: String?,
    private val enabled: Boolean = true,
    private val subLabel: String? = null,
    private val onClick: () -> Unit = {},
    private val overlay: @Composable () -> Unit = {},
) : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        if (label == null) return
        BookMoreMenuRow(
            label = label,
            icon = R.drawable.ic_book_detail_more_shelf,
            enabled = enabled,
            subLabel = subLabel,
            onClick = {
                dismissMenu()
                onClick()
            },
            modifier = Modifier.testTag(rowTag(label)),
        )
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        overlay()
    }
}

/** The test tag of the fake row labelled [label]. */
fun rowTag(label: String): String = "more-test-row-$label"

/** Sets a More button with [entries] alone, aligned to the top end, in dark theme. */
fun ComposeContentTestRule.setMoreButton(
    entries: List<BookMoreMenuEntry>,
    context: BookDetailSectionContext = testSectionContext(),
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            Box(
                Modifier.fillMaxSize().testTag(MORE_HOST),
                contentAlignment = Alignment.TopEnd,
            ) {
                BookMoreButtonContent(context, entries)
            }
        }
    }
}

/** The window root that holds the page, not a menu's popup window. */
fun ComposeContentTestRule.pageRoot(): SemanticsNodeInteraction =
    onAllNodes(isRoot()).filterToOne(hasAnyDescendant(hasTestTag(MORE_HOST)))

/** Closes the topmost popup with the Back key, the way a user does. */
fun ComposeContentTestRule.pressBackKey() {
    Espresso.pressBack()
    waitForIdle()
}

/** A hand-picked shelf row for sheet tests. */
fun manualShelfRow(
    id: Long,
    name: String = "Shelf $id",
    count: Long = 0,
    covers: List<String> = emptyList(),
    isOn: Boolean = false,
    isPending: Boolean = false,
): ManualShelfRow = ManualShelfRow(id, name, count, covers, isOn, isPending)

/** A read-only smart shelf row for sheet tests. */
fun smartShelfRow(
    id: Long,
    name: String = "Smart $id",
    count: Long = 0,
    covers: List<String> = emptyList(),
    rules: ShelfRules? = null,
): SmartShelfRow = SmartShelfRow(id, name, count, covers, rules)

/** A shelf's count text, such as "9 books", from the app's plurals. */
fun countText(count: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.resources
        .getQuantityString(R.plurals.book_detail_more_shelf_book_count, count, count)
