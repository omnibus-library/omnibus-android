package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.more.bookmarks.BookmarksSheetGround
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** Test tags for the file chooser, so UI tests can find its parts. */
internal object AudioFileChooserTestTags {
    const val SHEET = "audio-file-chooser-sheet"
    const val TITLE = "audio-file-chooser-title"
    const val CLOSE = "audio-file-chooser-close"

    /** The tag of the row for the file with [id]. */
    fun row(id: Long) = "audio-file-chooser-row-$id"
}

/**
 * The "Choose a file" bottom sheet for a book with several audio files (Android's counterpart of iOS `AudioFilePicker`).
 * A row tap hides the sheet, then reports the close and then the pick, so the page can open the player only after the
 * sheet has gone. Later taps during the hide are ignored. The close button and a swipe, the scrim or system back close the
 * sheet without picking.
 *
 * @param accent The colour of the leading icon and of the "Continues" line.
 * @param onClose Closes the sheet's state. Called before [onPick], and for every dismissal that picks nothing.
 * @param onPick Called with the row a tap chose, after [onClose].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioFileChooserSheet(
    choices: List<AudioFileChoice>,
    accent: Color,
    onClose: () -> Unit,
    onPick: (AudioFileChoice) -> Unit,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf(false) }
    val onChosen: (AudioFileChoice) -> Unit = { choice ->
        if (!chosen) {
            chosen = true
            scope.launch { sheetState.hide() }.invokeOnCompletion { cause ->
                if (cause == null) {
                    onClose()
                    onPick(choice)
                } else {
                    chosen = false
                }
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        containerColor = BookmarksSheetGround,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
    ) {
        AudioFileChooserContent(
            choices = choices,
            accent = accent,
            onClose = {
                scope.launch { sheetState.hide() }.invokeOnCompletion {
                    if (!sheetState.isVisible) onClose()
                }
            },
            onChosen = onChosen,
        )
    }
}

/**
 * The chooser's body, without the sheet window, so tests and previews can drive it. The title row stays at the top and
 * the rows scroll beneath it. The body is always dark: it paints its own ground in either app theme.
 *
 * @param onChosen Called with the row a tap chose.
 */
@Composable
internal fun AudioFileChooserContent(
    choices: List<AudioFileChoice>,
    accent: Color,
    onClose: () -> Unit,
    onChosen: (AudioFileChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    OmnibusTheme(darkTheme = true) {
        val palette = OmnibusTheme.palette
        Column(
            modifier
                .fillMaxWidth()
                .background(BookmarksSheetGround)
                .testTag(AudioFileChooserTestTags.SHEET)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.book_detail_listen_choose_file),
                    style = displayTextStyle(28.sp, palette.ink0, FontWeight.SemiBold),
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                        .testTag(AudioFileChooserTestTags.TITLE),
                )
                CloseButton(onClose)
            }
            Column(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                choices.forEach { choice -> ChooserRow(choice, accent, onChosen) }
            }
        }
    }
}

/** The 44 dp close button at the title's end. */
@Composable
private fun CloseButton(onClose: () -> Unit) {
    Box(
        Modifier
            .testTag(AudioFileChooserTestTags.CLOSE)
            .size(44.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_close),
            contentDescription = stringResource(R.string.book_detail_listen_close),
            tint = OmnibusTheme.palette.ink0,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * One file: the accent headphones, the name (at most two lines), the "FORMAT · size" detail, the continue or start-over
 * line, and a chevron.
 */
@Composable
private fun ChooserRow(choice: AudioFileChoice, accent: Color, onChosen: (AudioFileChoice) -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.bg1)
            .border(0.5.dp, palette.line2, shape)
            .clickable(role = Role.Button) { onChosen(choice) }
            .testTag(AudioFileChooserTestTags.row(choice.id))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.width(38.dp), contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.ic_category_audiobooks),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = choice.name,
                style = uiTextStyle(16, FontWeight.Medium, palette.ink0),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = audioFileDetail(choice.format, choice.sizeBytes),
                style = uiTextStyle(13, FontWeight.Normal, palette.ink3),
            )
            if (choice.continues) {
                Text(
                    text = stringResource(R.string.book_detail_listen_continues),
                    style = uiTextStyle(13, FontWeight.SemiBold, accent),
                )
            } else {
                Text(
                    text = stringResource(R.string.book_detail_listen_starts_over),
                    style = uiTextStyle(13, FontWeight.Normal, palette.ink3),
                )
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_merge_chevron),
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(14.dp),
        )
    }
}
