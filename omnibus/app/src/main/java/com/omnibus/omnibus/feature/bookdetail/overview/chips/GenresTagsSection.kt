package com.omnibus.omnibus.feature.bookdetail.overview.chips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.AddChip
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.ChipEditOverlay
import com.omnibus.omnibus.feature.bookdetail.overview.chips.edit.LocalChipEditorSource

/**
 * The Genres and Tags rows under the description. Each row is left out when the book has no values for it, and a tap
 * on a chip opens that genre or tag. A reader who may edit metadata also gets a "+" at the end of each row, and both
 * rows show even when empty (#101).
 */
internal object GenresTagsSection : BookDetailSection {
    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val editor = LocalChipEditorSource.current.editor(context)
        if (editor != null) LaunchedEffect(editor, context.book) { editor.bind(context.book) }
        val bookGenres = remember(context.book.genres) { chipValues(context.book.genres) }
        val bookTags = remember(context.book.subjects) { chipValues(context.book.subjects) }
        // An editor shows its own lists, which hold optimistic changes, once it is bound to this book.
        val live = editor?.state?.takeIf { it.bound }
        val genres = live?.genres ?: bookGenres
        val tags = live?.tags ?: bookTags
        val canEdit = editor != null
        val showGenres = showsChipRow(genres, canEdit)
        val showTags = showsChipRow(tags, canEdit)
        if (!showGenres && !showTags) return

        Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
            if (showGenres) {
                ValueChipRow(
                    label = stringResource(R.string.book_detail_chips_genres),
                    values = genres,
                    onChipClick = { context.onNavigate(BookDetailDestination.Genre(it)) },
                    modifier = Modifier.testTag(GenresTagsTestTags.GENRES_ROW),
                    trailing = {
                        if (editor != null) {
                            item(contentType = "add") {
                                AddChip(
                                    kind = MetadataListKind.Genres,
                                    enabled = editor.state.isOnline,
                                    onClick = { editor.open(MetadataListKind.Genres) },
                                )
                            }
                        }
                    },
                )
            }
            if (showTags) {
                ValueChipRow(
                    label = stringResource(R.string.book_detail_chips_tags),
                    values = tags,
                    onChipClick = { context.onNavigate(BookDetailDestination.Tag(it)) },
                    modifier = Modifier.testTag(GenresTagsTestTags.TAGS_ROW),
                    trailing = {
                        if (editor != null) {
                            item(contentType = "add") {
                                AddChip(
                                    kind = MetadataListKind.Tags,
                                    enabled = editor.state.isOnline,
                                    onClick = { editor.open(MetadataListKind.Tags) },
                                )
                            }
                        }
                    },
                )
            }
        }
        if (editor != null) ChipEditOverlay(editor, context)
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
