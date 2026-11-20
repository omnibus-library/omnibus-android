package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.components.ProviderCover
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The title-search stage: a title field, the providers' matches as rows, and a line when none match. Its column is
 * centred at a readable width from [wide]. The field takes focus when the stage opens.
 */
@Composable
internal fun ScanTitleSearchStage(stage: ScanStage.TitleSearch, actions: ScanActions, wide: Boolean) {
    val palette = OmnibusTheme.palette
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanCloseButton(actions.onClose)
            ScanTitleText(stringResource(R.string.scan_search_by_title), Modifier.weight(1f))
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
                onValueChange = actions.onTitleQueryChanged,
                placeholder = stringResource(R.string.scan_title_placeholder),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onTitleSearchSubmitted() }),
            )
            if (stage.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = palette.accent,
                    strokeWidth = 2.dp,
                )
            }
            InlineError(message = if (stage.failed) stringResource(R.string.scan_lookup_failed) else null)
            val results = stage.results
            if (results != null && results.isEmpty() && !stage.loading) {
                Text(
                    text = stringResource(R.string.scan_no_title_matches),
                    style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                )
            } else if (!results.isNullOrEmpty()) {
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(results) { record ->
                        ProviderResultRow(record) { actions.onSearchResultPicked(record) }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            TextLinkButton(
                text = stringResource(R.string.scan_scan_instead),
                color = palette.accent,
                size = 15,
                onClick = actions.onScanInstead,
                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth(),
            )
        }
    }
}

/** A provider's match: its cover, title, a line of author, publisher and pages or year, and a chevron. */
@Composable
private fun ProviderResultRow(record: ProviderRecord, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    val pagesText = record.pages?.let { stringResource(R.string.scan_fact_pages, it) }
    val yearText = record.firstPublishYear?.toString() ?: record.year?.takeIf { it.isNotBlank() }
    val facts = listOfNotNull(
        record.authors.firstOrNull(),
        record.publisher?.takeIf { it.isNotBlank() },
        pagesText ?: yearText,
    ).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.width(40.dp)) {
            ProviderCover(record.title, record.authors.firstOrNull(), record.coverUrl)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = record.title, style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0))
            if (facts.isNotEmpty()) {
                Text(text = facts, style = uiTextStyle(13, FontWeight.Normal, palette.ink2))
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
