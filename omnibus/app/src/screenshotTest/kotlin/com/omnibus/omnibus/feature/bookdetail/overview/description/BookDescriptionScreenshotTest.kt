package com.omnibus.omnibus.feature.bookdetail.overview.description

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.bookdetail.shotBook
import com.omnibus.omnibus.feature.bookdetail.shotSectionContext
import com.omnibus.omnibus.screenshot.Shot

/** A short description has no toggle and no space under it. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookDescriptionShort() {
    DescriptionShot("A short sentence about the book.")
}

/** A long description clamped to three lines, with Show more. */
@PreviewTest
@PreviewLightDark
@Composable
fun BookDescriptionClamped() {
    DescriptionShot(List(12) { "lorem ipsum dolor sit amet" }.joinToString(" "))
}

@Composable
private fun DescriptionShot(description: String) {
    Shot {
        BookDescription(shotSectionContext(shotBook(description = description)), Modifier.fillMaxWidth(), reducedMotion = true)
    }
}
