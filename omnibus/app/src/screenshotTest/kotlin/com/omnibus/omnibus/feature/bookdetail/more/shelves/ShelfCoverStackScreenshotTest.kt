package com.omnibus.omnibus.feature.bookdetail.more.shelves

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity

private val book = CoverIdentity(uuid = "uuid-1", title = "The Left Hand of Darkness", hasCover = false)
private val covers = listOf("c1", "c2", "c3", "c4", "c5")

/** Off, on, a single other cover with the book on, empty, and a dimmed smart shelf, side by side. */
@PreviewTest
@PreviewLightDark
@Composable
fun ShelfCoverStacks() {
    Shot {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ShelfCoverStack(1, covers, isOn = false, bookCover = book, reducedMotion = true)
            ShelfCoverStack(2, covers, isOn = true, bookCover = book, reducedMotion = true)
            ShelfCoverStack(3, listOf("c1"), isOn = true, bookCover = book, reducedMotion = true)
            ShelfCoverStack(4, emptyList(), isOn = false, bookCover = book, reducedMotion = true)
            ShelfCoverStack(5, covers, isOn = false, bookCover = null, reducedMotion = true, dimmed = true)
        }
    }
}
