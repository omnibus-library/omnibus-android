package com.omnibus.omnibus.feature.bookdetail.status

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.TimeZone

/** The label's string resource for [status]. */
internal fun ReadStatus.labelRes(): Int = when (this) {
    ReadStatus.Unread -> R.string.book_detail_status_unread
    ReadStatus.Reading -> R.string.book_detail_status_reading
    ReadStatus.Finished -> R.string.book_detail_status_finished
}

/**
 * The pill's rim: the book's tone at 22% alpha, darker on a light ground and lighter on a dark one. Pure, so tests
 * can check it.
 */
internal fun statusRim(tone: Oklch, lightGround: Boolean): Color =
    Oklch(if (lightGround) 0.42 else 0.80, tone.c, tone.h, alpha = 0.22).toColor()

/** The rim for [book] on the current theme. */
@Composable
internal fun rememberStatusRim(book: Book): Color {
    val light = isLightGround(OmnibusTheme.palette.bg0)
    return remember(book.accent, book.displayTitle, light) { statusRim(book.coverIdentity().tone, light) }
}

/**
 * The read status pill at the start of the stage's status row, with its menu of the three statuses. [uiState] is what
 * the pill shows and [onSelect] receives a choice of a different status. The menu closes on every choice.
 *
 * @param rim The 1 dp inner rim, from [rememberStatusRim].
 * @param reducedMotion When true, the label changes without an animation.
 */
@Composable
internal fun ReadStatusPill(
    uiState: ReadStatusUIState,
    accent: BookAccent,
    rim: Color,
    onSelect: (ReadStatus) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    var expanded by remember { mutableStateOf(false) }
    val label = uiState.status?.let { stringResource(it.labelRes()) }
        ?: stringResource(R.string.book_detail_status_unknown)
    val description = if (uiState.status != null) {
        stringResource(R.string.book_detail_status_cd, label)
    } else {
        stringResource(R.string.book_detail_status_cd_unknown)
    }
    val openLabel = stringResource(R.string.book_detail_status_open_menu)
    val labelStyle = uiTextStyle(15, FontWeight.SemiBold, palette.ink0)

    Box(modifier) {
        Row(
            Modifier
                .heightIn(min = 44.dp)
                .clip(CircleShape)
                .background(accent.tintSoft)
                .border(1.dp, rim, CircleShape)
                .clickable(role = Role.Button, onClickLabel = openLabel) { expanded = true }
                .padding(start = 15.dp, end = 12.dp)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .testTag(ReadStatusTestTags.PILL),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (reducedMotion) {
                Text(label, style = labelStyle)
            } else {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = {
                        (fadeIn(Motion.settle()) togetherWith fadeOut(Motion.settle())).using(
                            SizeTransform(clip = false) { _, _ -> Motion.settle() },
                        )
                    },
                    label = "read-status-label",
                ) { shown ->
                    Text(shown, style = labelStyle)
                }
            }
            Icon(
                imageVector = ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = palette.ink0,
            )
        }

        ReadStatusMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            uiState = uiState,
            onSelect = onSelect,
        )
    }
}

/** The three-row menu under the pill. Rows keep [ReadStatus] order, and the current one carries a check. */
@Composable
private fun ReadStatusMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    uiState: ReadStatusUIState,
    onSelect: (ReadStatus) -> Unit,
) {
    val palette = OmnibusTheme.palette
    val menuLabel = stringResource(R.string.book_detail_status_menu_label)
    val locale = LocalConfiguration.current.locales[0]
    val finishedDate = remember(uiState.finishedAtMillis, uiState.status, uiState.nowMillis, locale) {
        uiState.finishedAtMillis
            ?.takeIf { uiState.status == ReadStatus.Finished }
            ?.let { BookDetailFormat.shortDate(it, uiState.nowMillis, locale, TimeZone.getDefault()) }
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 232.dp)
            .semantics {
                contentDescription = menuLabel
                selectableGroup()
            }
            .testTag(ReadStatusTestTags.MENU),
        offset = DpOffset(0.dp, 4.dp),
        shape = RoundedCornerShape(16.dp),
        containerColor = palette.bg1,
        tonalElevation = 0.dp,
        border = BorderStroke(0.5.dp, palette.line2),
    ) {
        ReadStatus.entries.forEach { status ->
            val current = uiState.status == status
            val label = stringResource(status.labelRes())
            val date = finishedDate?.takeIf { status == ReadStatus.Finished }
            val rowDescription = date?.let { stringResource(R.string.book_detail_status_finished_on_cd, it) } ?: label

            DropdownMenuItem(
                text = { Text(label, style = uiTextStyle(17, FontWeight.Normal, palette.ink0)) },
                onClick = {
                    onDismiss()
                    if (!current) onSelect(status)
                },
                leadingIcon = {
                    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                        if (current) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp).testTag(ReadStatusTestTags.CHECK),
                                tint = palette.ink0,
                            )
                        }
                    }
                },
                trailingIcon = if (date == null) {
                    null
                } else {
                    { FinishedDateText(date) }
                },
                contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag(ReadStatusTestTags.row(status))
                    .semantics(mergeDescendants = true) {
                        role = Role.RadioButton
                        selected = current
                        contentDescription = rowDescription
                    },
            )
        }
    }
}

/** The Finished row's trailing date, such as "Oct 29". */
@Composable
private fun FinishedDateText(date: String) {
    Text(
        date,
        style = uiTextStyle(14, FontWeight.Normal, OmnibusTheme.palette.ink1),
        modifier = Modifier.testTag(ReadStatusTestTags.FINISHED_DATE),
    )
}

/** The 14 dp chevron, drawn in code. */
private val ChevronDown: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReadStatusChevron",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.4f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(6f, 9f)
        lineTo(12f, 15f)
        lineTo(18f, 9f)
    }.build()
}

@PreviewLightDark
@Composable
private fun ReadStatusPillUnknownPreview() {
    ReadStatusPillPreview(ReadStatusUIState())
}

@PreviewLightDark
@Composable
private fun ReadStatusPillUnreadPreview() {
    ReadStatusPillPreview(ReadStatusUIState(status = ReadStatus.Unread))
}

@PreviewLightDark
@Composable
private fun ReadStatusPillReadingPreview() {
    ReadStatusPillPreview(ReadStatusUIState(status = ReadStatus.Reading))
}

@PreviewLightDark
@Composable
private fun ReadStatusPillFinishedPreview() {
    ReadStatusPillPreview(
        ReadStatusUIState(status = ReadStatus.Finished, finishedAtMillis = 1_761_746_400_000L, nowMillis = 1_763_200_000_000L),
    )
}

@Composable
private fun ReadStatusPillPreview(uiState: ReadStatusUIState) {
    val tone = Oklch(0.62, 0.10, 200.0)
    OmnibusTheme {
        ReadStatusPill(
            uiState = uiState,
            accent = bookAccent(tone, lightGround = false),
            rim = statusRim(tone, lightGround = false),
            onSelect = {},
            reducedMotion = true,
        )
    }
}
