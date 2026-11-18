package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.ui.theme.UiFontFamily
import com.omnibus.omnibus.ui.theme.labelRes
import com.omnibus.omnibus.ui.theme.tint

/**
 * The selection menu's body and tail as one outline, so its 1 dp border has no seam. The tail points up when [tailUp]
 * (the menu is below the selection) and down otherwise, with its centre at [tailCenterX] from the menu's left edge.
 */
internal class SelectionMenuShape(
    private val tailUp: Boolean,
    private val tailCenterX: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline = with(density) {
        val tail = SelectionMenuMetrics.TAIL_HEIGHT.dp.toPx()
        val half = (SelectionMenuMetrics.TAIL_WIDTH / 2).dp.toPx()
        val top = if (tailUp) tail else 0f
        val bottom = if (tailUp) size.height else size.height - tail
        val body = Path().apply {
            addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(SelectionMenuMetrics.CORNER.dp.toPx())))
        }
        val cx = tailCenterX.toPx()
        val tip = Path().apply {
            if (tailUp) {
                moveTo(cx - half, top)
                lineTo(cx, 0f)
                lineTo(cx + half, top)
            } else {
                moveTo(cx - half, bottom)
                lineTo(cx, size.height)
                lineTo(cx + half, bottom)
            }
            close()
        }
        Outline.Generic(Path().also { it.op(body, tip, PathOperation.Union) })
    }
}

/**
 * The highlight menu over the page: five colour swatches in [HighlightColor] order, a divider, and Copy. It is drawn at
 * [placement] with its tail on the selection, in the reading theme's [colors]. Its size is fixed, so placement needs no
 * measure pass.
 */
@Composable
internal fun ReaderSelectionMenu(
    placement: SelectionMenuPlacement,
    colors: ReaderChromeColors,
    onColorChosen: (HighlightColor) -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = SelectionMenuShape(tailUp = placement.tailUp, tailCenterX = placement.tailCenterX.dp)
    val tailPadding = SelectionMenuMetrics.TAIL_HEIGHT.dp
    Box(
        modifier
            .offset(placement.left.dp, placement.top.dp)
            .size(placement.width.dp, SelectionMenuMetrics.HEIGHT.dp)
            .testTag(ReaderSelectionTags.MENU),
    ) {
        // The shadow goes on the body alone, since a tail outline casts none before API 29.
        Box(
            Modifier
                .fillMaxWidth()
                .height(SelectionMenuMetrics.BODY_HEIGHT.dp)
                .align(if (placement.tailUp) Alignment.BottomStart else Alignment.TopStart)
                .shadow(8.dp, RoundedCornerShape(SelectionMenuMetrics.CORNER.dp)),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.plate, shape)
                .border(1.dp, colors.border, shape),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (placement.tailUp) tailPadding else 0.dp,
                        bottom = if (placement.tailUp) 0.dp else tailPadding,
                    ),
            ) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp)) {
                    HighlightColor.entries.forEach { color ->
                        val label = stringResource(color.labelRes)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(role = Role.Button) { onColorChosen(color) }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(color.tint(), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.border)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .height(55.5.dp)
                        .clickable(role = Role.Button, onClick = onCopy),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_reader_copy),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.ink,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.reader_selection_copy),
                        fontFamily = UiFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = colors.ink,
                    )
                }
            }
        }
    }
}
