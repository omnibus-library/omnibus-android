package com.omnibus.omnibus.feature.bookdetail.overview.chips

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.feature.bookdetail.shotSectionContext
import com.omnibus.omnibus.screenshot.Shot

// Preview mode has no chip editor, so these are a reader's rows.

@PreviewTest
@PreviewLightDark
@Composable
fun GenresTagsBothRows() {
    GenresTagsShot(genres = listOf("Science Fiction", "Dystopia"), tags = listOf("Utopia", "Anthropology"))
}

@PreviewTest
@PreviewLightDark
@Composable
fun GenresTagsGenresOnly() {
    GenresTagsShot(genres = listOf("Fantasy"), tags = emptyList())
}

@PreviewTest
@PreviewLightDark
@Composable
fun GenresTagsTagsOnly() {
    GenresTagsShot(genres = emptyList(), tags = listOf("Epic"))
}

@Composable
private fun GenresTagsShot(genres: List<String>, tags: List<String>) {
    Shot {
        GenresTagsSection.Content(shotSectionContext(shotBook(genres = genres, subjects = tags)), Modifier)
    }
}
