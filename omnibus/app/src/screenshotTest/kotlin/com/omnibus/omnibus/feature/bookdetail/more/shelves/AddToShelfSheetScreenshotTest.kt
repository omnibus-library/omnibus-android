package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRule
import com.omnibus.omnibus.data.shelves.ShelfRuleField
import com.omnibus.omnibus.data.shelves.ShelfRuleOp
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.screenshot.Shot

// The loading spinner is indeterminate, so that state is not shot; AddToShelfSheetTest covers it.

/** The sheet's container colour. */
private val SheetContainer = Color(0xFF15120F)

/** The intro, then manual rows in server order with plural counts, off and on (the on row shows this book's cover). */
@PreviewTest
@Preview
@Composable
fun AddToShelfSheetManualRows() {
    ShelfSheetShot(
        ShelfSheetContent.Loaded(
            manual = listOf(
                manual(1, "Currently reading", count = 1),
                manual(2, "Empty shelf", count = 0),
                manual(3, "Favourites", count = 12, covers = listOf("c1", "c2"), isOn = true),
            ),
            smart = emptyList(),
        ),
    )
}

/** The smart section under the manual rows: its note, a row with its rule text and one with a count only. */
@PreviewTest
@Preview
@Composable
fun AddToShelfSheetSmartSection() {
    val year = ShelfRules(matchMode = ShelfMatchMode.All, rules = listOf(ShelfRule(ShelfRuleField.Year, ShelfRuleOp.Gte, "1901")))
    ShelfSheetShot(
        ShelfSheetContent.Loaded(
            manual = listOf(manual(1, "Shelf", count = 5)),
            smart = listOf(
                SmartShelfRow(10, "Old favourites", 14, listOf("a", "b", "c"), year),
                SmartShelfRow(11, "In progress", 3, emptyList(), null),
            ),
        ),
    )
}

@PreviewTest
@Preview
@Composable
fun AddToShelfSheetEmpty() {
    ShelfSheetShot(ShelfSheetContent.Loaded(emptyList(), emptyList()))
}

@PreviewTest
@Preview
@Composable
fun AddToShelfSheetFailed() {
    ShelfSheetShot(ShelfSheetContent.Failed)
}

private fun manual(id: Long, name: String, count: Long, covers: List<String> = emptyList(), isOn: Boolean = false) =
    ManualShelfRow(id, name, count, covers, isOn, isPending = false)

@Composable
private fun ShelfSheetShot(content: ShelfSheetContent) {
    // The sheet draws a fixed dark container but takes its text colours from the app theme, so it is only legible dark.
    Shot(darkTheme = true) {
        Box(Modifier.background(SheetContainer)) {
            AddToShelfSheetContent(
                content = content,
                book = shotBook(),
                onToggle = {},
                onRetry = {},
                onClose = {},
                reducedMotion = true,
            )
        }
    }
}
