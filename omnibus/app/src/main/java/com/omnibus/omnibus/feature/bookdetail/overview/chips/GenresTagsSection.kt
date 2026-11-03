package com.omnibus.omnibus.feature.bookdetail.overview.chips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/**
 * The Genres and Tags rows under the description. Each row is left out when the book has no values for it, and a tap
 * on a chip opens that genre or tag.
 */
internal object GenresTagsSection : BookDetailSection {
    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val genres = remember(context.book.genres) { chipValues(context.book.genres) }
        val tags = remember(context.book.subjects) { chipValues(context.book.subjects) }
        if (genres.isEmpty() && tags.isEmpty()) return

        Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (genres.isNotEmpty()) {
                ValueChipRow(
                    label = stringResource(R.string.book_detail_chips_genres),
                    values = genres,
                    onChipClick = { context.onNavigate(BookDetailDestination.Genre(it)) },
                    modifier = Modifier.testTag(GenresTagsTestTags.GENRES_ROW),
                )
            }
            if (tags.isNotEmpty()) {
                ValueChipRow(
                    label = stringResource(R.string.book_detail_chips_tags),
                    values = tags,
                    onChipClick = { context.onNavigate(BookDetailDestination.Tag(it)) },
                    modifier = Modifier.testTag(GenresTagsTestTags.TAGS_ROW),
                )
            }
        }
    }
}

/** A [ChipRow] of one chip per value, followed by [trailing] items. */
@Composable
internal fun ValueChipRow(
    label: String,
    values: List<String>,
    onChipClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    trailing: LazyListScope.() -> Unit = {},
) {
    ChipRow(label, modifier) {
        items(values, key = { it }) { value ->
            StageChip(value, onClick = { onChipClick(value) }, modifier = Modifier.testTag(GenresTagsTestTags.CHIP))
        }
        trailing()
    }
}
