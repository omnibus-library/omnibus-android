package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.IsbnRejection
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The typed-ISBN stage. The field keeps what the reader typed; only the check and the lookup clean it. */
@Composable
internal fun ScanManualStage(stage: ScanStage.Manual, actions: ScanActions, wide: Boolean) {
    val palette = OmnibusTheme.palette
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ScanCloseButton(actions.onClose)
            ScanTitleText(stringResource(R.string.scan_manual_title), Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (wide) Modifier.widthIn(max = SCAN_COLUMN_MAX_WIDTH) else Modifier)
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.scan_manual_hint),
                style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
            )
            OmnibusTextField(
                value = stage.text,
                onValueChange = { typed -> actions.onIsbnChanged(typed.filter(::isIsbnCharacter)) },
                placeholder = stringResource(R.string.scan_manual_placeholder),
                modifier = Modifier.testTag("scan-manual-field"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onFindBook() }),
            )
            InlineError(
                message = stage.shownRejection?.let { stringResource(it.messageRes()) },
                modifier = Modifier.testTag("scan-manual-error"),
            )
            FilledButton(
                text = stringResource(R.string.scan_find_book),
                onClick = actions.onFindBook,
                enabled = stage.canFind,
            )
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

/** The characters the field accepts: digits, X (an ISBN-10 check digit), and the hyphens and spaces people type. */
private fun isIsbnCharacter(c: Char): Boolean = c in '0'..'9' || c == 'x' || c == 'X' || c == '-' || c == ' '

private fun IsbnRejection.messageRes(): Int = when (this) {
    IsbnRejection.WrongLength -> R.string.scan_isbn_wrong_length
    IsbnRejection.BadCheckDigit -> R.string.scan_isbn_bad_check_digit
    IsbnRejection.InvalidCharacters -> R.string.scan_isbn_invalid_characters
}
