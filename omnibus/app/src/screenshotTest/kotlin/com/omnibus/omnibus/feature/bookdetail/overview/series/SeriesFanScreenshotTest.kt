package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.screenshot.Shot

private val Ribbon = Color(0xFFB8864A)

/** The gathered hand with the ribbon on this book, the third of five. */
@PreviewTest
@PreviewLightDark
@Composable
fun SeriesFanGathered() {
    SeriesFanShot(fanModel(5, current = 2), spread = false)
}

@PreviewTest
@PreviewLightDark
@Composable
fun SeriesFanSpread() {
    SeriesFanShot(fanModel(5, current = 2), spread = true)
}

/** A book that is not in the list: no ribbon, and every card at the same height. */
@PreviewTest
@PreviewLightDark
@Composable
fun SeriesFanAbsentBook() {
    SeriesFanShot(fanModel(4, current = null), spread = false)
}

/** [n] members "m1".."m$n" with this book at [current] (0-based), or absent when null. */
private fun fanModel(n: Int, current: Int?): SeriesFanModel {
    val members = (1..n).map { SeriesMember("m$it", "Title $it", "$it", null, null, false) }
    val book = shotBook(series = "Dune", seriesIndex = current?.let { "${it + 1}" })
        .copy(uniqueIdentifier = current?.let { "m${it + 1}" } ?: "absent", seriesId = 4L)
    return seriesFanModel(book, members)!!
}

@Composable
private fun SeriesFanShot(model: SeriesFanModel, spread: Boolean) {
    Shot {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            SeriesFan(
                model = model,
                spread = spread,
                onToggleSpread = {},
                onOpenBook = {},
                onOpenSeriesPage = {},
                ribbonColor = Ribbon,
                reducedMotion = true,
            )
        }
    }
}
