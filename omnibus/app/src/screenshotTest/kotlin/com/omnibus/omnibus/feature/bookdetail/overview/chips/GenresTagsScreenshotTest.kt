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
    Shot {
        GenresTagsSection.Content(
            shotSectionContext(shotBook(genres = listOf("Science Fiction", "Dystopia"), subjects = listOf("Utopia", "Anthropology"))),
            Modifier,
        )
    }
}
