package com.omnibus.omnibus.feature.bookdetail.files.details

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

private const val VALUE_MAX_LINES = 2
private val LabelValueGap = 16.dp

/**
 * One "label, value" row. The value is right-aligned on up to two lines and cut in the middle when it is longer,
 * and it can be selected; copying a cut value copies all of it. The row reads to TalkBack as one item, "label,
 * value", with the whole value.
 */
@Composable
internal fun DetailRowView(item: DetailItem, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val label = stringResource(item.row.labelRes())
    val description = stringResource(R.string.book_detail_details_row_description, label, item.value)
    val labelStyle = uiTextStyle(15.sp, FontWeight.Normal, palette.ink1)
    val valueStyle = uiTextStyle(15.sp, FontWeight.Medium, palette.ink0).copy(textAlign = TextAlign.End)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val lineColor = palette.line

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .testTag(BookDetailsTestTags.row(item.row))
            .semantics(mergeDescendants = true) { contentDescription = description }
            .drawBehind {
                val rule = 1.dp.toPx()
                drawRect(lineColor, topLeft = Offset(0f, size.height - rule), size = Size(size.width, rule))
            },
    ) {
        // The value gets what the label and the gap leave, so the cut is made against the width it will really have.
        val labelWidth = remember(label, labelStyle, density) {
            measurer.measure(label, labelStyle, maxLines = 1, softWrap = false).size.width
        }
        val valueWidth = (constraints.maxWidth - labelWidth - with(density) { LabelValueGap.roundToPx() }).coerceAtLeast(0)
        val shown = remember(item.value, valueWidth, valueStyle, density) {
            fitMiddle(item.value) { candidate ->
                !measurer.measure(
                    candidate,
                    valueStyle,
                    maxLines = VALUE_MAX_LINES,
                    constraints = Constraints(maxWidth = valueWidth),
                ).hasVisualOverflow
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(LabelValueGap),
        ) {
            Text(label, style = labelStyle, maxLines = 1)
            CopiesWholeValue(whole = item.value, enabled = shown != item.value, modifier = Modifier.weight(1f)) {
                Text(
                    text = shown,
                    style = valueStyle,
                    maxLines = VALUE_MAX_LINES,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().testTag(BookDetailsTestTags.value(item.row)),
                )
            }
        }
    }
}

@StringRes
private fun DetailRow.labelRes(): Int = when (this) {
    DetailRow.Publisher -> R.string.book_detail_details_publisher
    DetailRow.Published -> R.string.book_detail_details_published
    DetailRow.Series -> R.string.book_detail_details_series
    DetailRow.Language -> R.string.book_detail_details_language
    DetailRow.Isbn -> R.string.book_detail_details_isbn
    DetailRow.Added -> R.string.book_detail_details_added
    DetailRow.File -> R.string.book_detail_details_file
}

/**
 * A [SelectionContainer] whose Copy puts [whole] on the clipboard, for a value shown cut in the middle ([enabled]),
 * so a copied value is never the cut text with its ellipsis.
 */
@Composable
private fun CopiesWholeValue(
    whole: String,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val delegate = LocalTextToolbar.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val toolbar = remember(delegate, clipboard, whole) {
        WholeValueToolbar(delegate) {
            scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, whole))) }
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides if (enabled) toolbar else delegate) {
        SelectionContainer(modifier, content = content)
    }
}

/** A [TextToolbar] that shows the [delegate]'s menu, and after its Copy runs [copyWhole], which overwrites the clipboard. */
private class WholeValueToolbar(
    private val delegate: TextToolbar,
    private val copyWhole: () -> Unit,
) : TextToolbar {
    override val status: TextToolbarStatus get() = delegate.status

    override fun hide() = delegate.hide()

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) = delegate.showMenu(
        rect,
        onCopyRequested = onCopyRequested?.let { copy ->
            {
                copy()
                copyWhole()
            }
        },
        onPasteRequested = onPasteRequested,
        onCutRequested = onCutRequested,
        onSelectAllRequested = onSelectAllRequested,
    )
}
