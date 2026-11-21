package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.LibraryHit
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The library picker, opened by "I already have this book": a field that searches the library's books, the hits as
 * rows, and a note when there are more matches than rows. Back returns to the outcome it came from.
 */
@Composable
internal fun ScanLibraryPickerStage(stage: ScanStage.LibraryPicker, actions: ScanActions, wide: Boolean) {
    val palette = OmnibusTheme.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = actions.onBack,
                modifier = Modifier.size(48.dp).testTag(ScanTestTags.BACK),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.scan_back),
                    tint = palette.ink0,
                )
            }
            ScanTitleText(stringResource(R.string.scan_picker_title), Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .then(if (wide) Modifier.widthIn(max = SCAN_COLUMN_MAX_WIDTH) else Modifier)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OmnibusTextField(
                value = stage.query,
                onValueChange = actions.onLibraryQueryChanged,
                placeholder = stringResource(R.string.scan_picker_placeholder),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            if (stage.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp,
                )
            }
            InlineError(message = if (stage.failed) stringResource(R.string.scan_lookup_failed) else null)
            if (stage.hits.isEmpty() && stage.query.isNotBlank() && !stage.loading && !stage.failed) {
                Text(
                    text = stringResource(R.string.scan_picker_no_matches),
                    style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                )
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(stage.hits) { hit ->
                    LibraryHitRow(hit) { actions.onLibraryHitPicked(hit) }
                }
            }
            if (stage.total > stage.hits.size) {
                Text(
                    text = stringResource(R.string.scan_picker_truncated, stage.hits.size, stage.total),
                    style = uiTextStyle(13, FontWeight.Normal, palette.ink2),
                )
            }
        }
    }
}

/** A library book the search found: its cover, title, author and a chevron. */
@Composable
private fun LibraryHitRow(hit: LibraryHit, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        ScanLibraryCover(
            uuid = hit.uuid,
            title = hit.title,
            authors = listOfNotNull(hit.authorDisplay.takeIf { it.isNotBlank() }),
            hasCover = hit.coverUrl != null,
            modifier = Modifier.width(40.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = hit.title, style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0))
            if (hit.authorDisplay.isNotBlank()) {
                Text(text = hit.authorDisplay, style = uiTextStyle(13, FontWeight.Normal, palette.ink2))
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_reader_chevron_right),
            contentDescription = null,
            tint = palette.ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}
