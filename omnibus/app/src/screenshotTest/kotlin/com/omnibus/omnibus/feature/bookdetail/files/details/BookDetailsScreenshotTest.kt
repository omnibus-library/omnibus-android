package com.omnibus.omnibus.feature.bookdetail.files.details

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun BookDetailsRows() {
    BookDetailsShot(
        listOf(
            DetailItem(DetailRow.Publisher, "Spencer Blackett"),
            DetailItem(DetailRow.Language, "English"),
            DetailItem(DetailRow.File, "hound.epub"),
        ),
    )
}

/** A long value is cut in the middle, on two lines. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookDetailsLongValue() {
    BookDetailsShot(listOf(DetailItem(DetailRow.File, "Start_" + "x".repeat(300) + "_end.epub")))
}

@Composable
private fun BookDetailsShot(items: List<DetailItem>) {
    Shot {
        BookDetails(items, Modifier.fillMaxWidth())
    }
}
