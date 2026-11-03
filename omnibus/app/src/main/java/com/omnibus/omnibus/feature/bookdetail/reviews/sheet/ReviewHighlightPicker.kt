package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRow
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The picker sheet's ground, a little lighter than the review sheet's. */
private val PickerGround = Color(0xFF1C1814)

/**
 * The "From your highlights" list: the reader's highlights with text, one tap to quote one. It shows the loading,
 * failed and empty states in place of the list.
 *
 * @param content What the list shows.
 * @param nowMillis The time the rows' dates are shown relative to.
 * @param onPick Called with the id of the highlight the reader tapped.
 * @param onRetry Called when the reader retries after a failed load.
 * @param onClose Called when the reader closes the picker.
 */
@Composable
internal fun ReviewHighlightPickerContent(
    content: PickerContent,
    nowMillis: Long,
    onPick: (Long) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val pickLabel = stringResource(R.string.book_detail_review_sheet_picker_pick)
    val closeLabel = stringResource(R.string.book_detail_review_sheet_picker_close)

    Column(
        modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .background(PickerGround)
            .testTag(ReviewSheetTestTags.PICKER),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.book_detail_review_sheet_picker_title),
                style = displayTextStyle(26.sp, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(ReviewSheetTestTags.PICKER_TITLE),
            )
            Box(
                Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = closeLabel }
                    .testTag(ReviewSheetTestTags.PICKER_CLOSE),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CloseIcon,
                    contentDescription = null,
                    tint = palette.ink0,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = stringResource(R.string.book_detail_review_sheet_picker_subtitle),
            style = uiTextStyle(14.sp, FontWeight.Normal, Color(0xFFC8C4BE)),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (content) {
                PickerContent.Loading -> LoadingStateView(
                    label = stringResource(R.string.book_detail_review_sheet_picker_loading),
                    background = PickerGround,
                )
                PickerContent.Failed -> ErrorStateView(
                    title = stringResource(R.string.book_detail_review_sheet_picker_failed_title),
                    message = stringResource(R.string.book_detail_review_sheet_picker_failed_message),
                    actionLabel = stringResource(R.string.book_detail_review_sheet_picker_retry),
                    onRetry = onRetry,
                    background = PickerGround,
                )
                is PickerContent.Loaded -> if (content.rows.isEmpty()) {
                    Box(Modifier.fillMaxSize().background(PickerGround), contentAlignment = Alignment.Center) {
                        EmptyStateView(
                            title = stringResource(R.string.book_detail_review_sheet_picker_empty_title),
                            message = stringResource(R.string.book_detail_review_sheet_picker_empty_message),
                        )
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 34.dp)) {
                        items(content.rows, key = { it.id }) { row ->
                            HighlightRow(
                                row = row,
                                nowMillis = nowMillis,
                                modifier = Modifier.clickable(onClickLabel = pickLabel, role = Role.Button) {
                                    onPick(row.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A close cross, stroked in the current tint. */
private val CloseIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewPickerClose",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M6 6l12 12M18 6 6 18"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.1f,
            strokeLineCap = StrokeCap.Round,
        )
        .build()
}
