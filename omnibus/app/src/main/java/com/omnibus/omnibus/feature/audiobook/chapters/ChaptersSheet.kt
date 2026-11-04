package com.omnibus.omnibus.feature.audiobook.chapters

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.reader.ui.ContentsSheetHeightFraction
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Test tags for the chapters sheet. */
internal object ChaptersTestTags {
    const val SHEET = "audiobook_chapters_sheet"
    const val TITLE = "audiobook_chapters_title"
    const val DONE = "audiobook_chapters_done"
    const val LIST = "audiobook_chapters_list"

    /** The progress line, which only the current row has. */
    const val PROGRESS = "audiobook_chapters_progress"

    /** The accent fill inside [PROGRESS]. */
    const val PROGRESS_FILL = "audiobook_chapters_progress_fill"

    fun row(index: Int) = "audiobook_chapters_row_$index"
    fun number(index: Int) = "audiobook_chapters_number_$index"
    fun title(index: Int) = "audiobook_chapters_row_title_$index"
    fun length(index: Int) = "audiobook_chapters_length_$index"
}

/**
 * The player's chapters sheet (P-CHP-1 to P-CHP-5), mounted by `PlayerScreen` for the `Chapters` overlay. Its window is
 * [ReaderContentsSheet]'s. A tap on a row seeks and plays through [viewModel], then hides the sheet and reports.
 * Later taps during the hide are ignored.
 *
 * @param onDismiss Clears the overlay. Also called for a swipe, the scrim, Done or system back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChaptersSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChaptersViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    var chosen by remember { mutableStateOf(false) }
    val hideThenDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    // The player is always dark, whatever the system theme.
    OmnibusTheme(darkTheme = true) {
        val palette = OmnibusTheme.palette
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            modifier = modifier,
            sheetState = sheetState,
            containerColor = palette.bg0,
            contentColor = palette.ink0,
            dragHandle = { BottomSheetDefaults.DragHandle(color = palette.line2) },
        ) {
            ChaptersSheetContent(
                state = uiState,
                onChosen = { index ->
                    if (!chosen) {
                        chosen = true
                        viewModel.onChapterChosen(index)
                        hideThenDismiss()
                    }
                },
                onDone = hideThenDismiss,
                // The 8% left over keeps a strip of scrim above the sheet, as the reader's contents sheet does.
                modifier = Modifier.fillMaxWidth().fillMaxHeight(ContentsSheetHeightFraction),
            )
        }
    }
}

/**
 * The sheet's body without its window, so tests and previews can drive it. The header stays fixed and the rows scroll
 * beneath it. The list opens with the current chapter centred (P-CHP-4). Draws on the ambient theme.
 *
 * @param onChosen Called with the index of the row that was tapped.
 * @param onDone Called when the Done pill is tapped.
 */
@Composable
internal fun ChaptersSheetContent(
    state: ChaptersUiState,
    onChosen: (index: Int) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val listState = rememberLazyListState()

    Column(modifier.background(palette.bg0).testTag(ChaptersTestTags.SHEET)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.lg),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.audiobook_chapters_title),
                style = displayTextStyle(23.sp, palette.ink0),
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }.testTag(ChaptersTestTags.TITLE),
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(palette.bg2)
                    .clickable(role = Role.Button, onClick = onDone)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm)
                    .testTag(ChaptersTestTags.DONE),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.audiobook_chapters_done),
                    style = uiTextStyle(16.sp, FontWeight.Medium, palette.accent),
                )
            }
        }

        ChapterList(state, listState, onChosen)
    }
}

/**
 * The rows in one rounded card. The current chapter is scrolled to the middle of the list once, when the list first
 * appears. Near either end the scroll stops where the list does.
 */
@Composable
private fun ChapterList(state: ChaptersUiState, listState: LazyListState, onChosen: (Int) -> Unit) {
    val palette = OmnibusTheme.palette
    val currentIndex = state.currentIndex

    LaunchedEffect(Unit) {
        val index = currentIndex ?: return@LaunchedEffect
        listState.scrollToItem(index)
        // Wait for the layout that contains the item, then pull it to the middle of the viewport.
        val info = snapshotFlow { listState.layoutInfo }
            .first { layout -> layout.visibleItemsInfo.any { it.index == index } }
        val item = info.visibleItemsInfo.first { it.index == index }
        val viewport = info.viewportEndOffset - info.viewportStartOffset
        listState.scrollBy(-((viewport - item.size) / 2f))
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen)
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(palette.bg1)
            .testTag(ChaptersTestTags.LIST),
        contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm),
    ) {
        // Keyed by index: titles can repeat.
        items(count = state.rows.size, key = { it }) { index ->
            ChapterRow(
                index = index,
                row = state.rows[index],
                isCurrent = index == currentIndex,
                progress = state.currentProgress,
                onClick = { onChosen(index) },
            )
        }
    }
}

/** One row: a hairline above it (none above the first), the number, the title with the progress line, and the length. */
@Composable
private fun ChapterRow(
    index: Int,
    row: ChapterRowModel,
    isCurrent: Boolean,
    progress: Float,
    onClick: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val description = stringResource(R.string.audiobook_chapters_row_description, row.number, row.title, row.lengthLabel)
    val currentLabel = stringResource(R.string.audiobook_chapters_current)

    Column {
        if (index > 0) {
            HorizontalDivider(thickness = 0.5.dp, color = palette.line)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (isCurrent) stateDescription = currentLabel
                }
                .testTag(ChaptersTestTags.row(index))
                .padding(vertical = 13.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = row.number.toString(),
                style = monoTextStyle(13.sp, if (isCurrent) palette.accent else palette.ink2),
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = 20.dp).padding(top = 3.dp).testTag(ChaptersTestTags.number(index)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.title,
                    style = uiTextStyle(
                        16.sp,
                        if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                        if (isCurrent) palette.ink0 else palette.ink1,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(ChaptersTestTags.title(index)),
                )
                if (isCurrent) {
                    ProgressLine(progress)
                }
            }
            Text(
                text = row.lengthLabel,
                style = monoTextStyle(13.sp, palette.ink2),
                modifier = Modifier.padding(top = 3.dp).testTag(ChaptersTestTags.length(index)),
            )
        }
    }
}

/** The thin accent line under the current title: a track, filled to [progress] of its width (P-CHP-3). */
@Composable
private fun ProgressLine(progress: Float) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(CircleShape)
            .background(palette.line)
            .testTag(ChaptersTestTags.PROGRESS),
    ) {
        Box(
            Modifier.fillMaxWidth(progress).fillMaxHeight().background(palette.accent).testTag(ChaptersTestTags.PROGRESS_FILL),
        )
    }
}

private val PreviewRows = listOf(
    "Opening Credits" to "0:42",
    "Chapter 1: The Long Road Home" to "21:19",
    "Chapter 2" to "28:19",
    "Chapter 3: A title long enough that it has to wrap onto a second line and then end in an ellipsis" to "23:59",
    "Chapter 4" to "1:18:19",
    "Chapter 5" to "17:07",
).mapIndexed { index, (title, length) -> ChapterRowModel(index + 1, title, length) }

@PreviewLightDark
@Composable
private fun ChaptersSheetContentPreview() {
    OmnibusTheme(darkTheme = true) {
        ChaptersSheetContent(
            state = ChaptersUiState(rows = PreviewRows, currentIndex = 3, currentProgress = 0.4f),
            onChosen = {},
            onDone = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
