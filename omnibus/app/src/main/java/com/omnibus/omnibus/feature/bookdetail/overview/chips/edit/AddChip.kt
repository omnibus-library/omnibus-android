package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.overview.chips.ChipHeight
import com.omnibus.omnibus.feature.bookdetail.overview.chips.ChipTouchSize
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** The plus glyph's size in dp, which is the 24-unit icon drawn at 14 dp. */
private const val GLYPH_DP = 14f

/** The plus glyph's stroke in dp: the icon's 2.2-unit stroke at 14 dp. */
private const val GLYPH_STROKE_DP = GLYPH_DP * 2.2f / 24f

/**
 * The dashed "+" at the end of a chip row, which opens the editor for [kind]. It has a touch target of at least
 * [ChipTouchSize] and is drawn at 40% alpha while [enabled] is false.
 */
@Composable
internal fun AddChip(
    kind: MetadataListKind,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val isGenres = kind == MetadataListKind.Genres
    val description = stringResource(
        if (isGenres) R.string.book_detail_chipedit_add_genres else R.string.book_detail_chipedit_add_tags,
    )
    Box(
        modifier
            .size(ChipTouchSize)
            .testTag(if (isGenres) ChipEditTestTags.ADD_GENRES else ChipEditTestTags.ADD_TAGS)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(ChipHeight)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    drawCircle(
                        color = palette.ink0.copy(alpha = 0.3f),
                        radius = size.minDimension / 2 - stroke / 2,
                        style = Stroke(
                            width = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                        ),
                    )
                    val arm = GLYPH_DP.dp.toPx() * 14f / 24f / 2
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val glyphStroke = GLYPH_STROKE_DP.dp.toPx()
                    drawLine(
                        color = palette.ink1,
                        start = Offset(cx - arm, cy),
                        end = Offset(cx + arm, cy),
                        strokeWidth = glyphStroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = palette.ink1,
                        start = Offset(cx, cy - arm),
                        end = Offset(cx, cy + arm),
                        strokeWidth = glyphStroke,
                        cap = StrokeCap.Round,
                    )
                },
        )
    }
}
