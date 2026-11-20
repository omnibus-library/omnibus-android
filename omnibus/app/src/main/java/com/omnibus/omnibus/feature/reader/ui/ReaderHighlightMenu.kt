package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.feature.reader.HighlightMenu
import com.omnibus.omnibus.ui.theme.UiFontFamily
import com.omnibus.omnibus.ui.theme.labelRes
import com.omnibus.omnibus.ui.theme.tint

/** Test tags on the highlight menu's nodes, so tests can find them without depending on the layout. */
internal object ReaderHighlightMenuTags {
    const val MENU = "reader_highlight_menu"
    const val SCRIM = "reader_highlight_scrim"
    const val OUTLINE = "reader_highlight_outline"
    const val POSITION = "reader_highlight_position"
    const val DELETE = "reader_highlight_delete"
}

/**
 * The highlight menu over the page, with the outline of the active highlight when several are under the tap, and a
 * scrim that takes every touch outside the menu. Any touch on the scrim calls [onDismiss] once it lifts, and never
 * reaches the page beneath. All coordinates are the page's dp, so [modifier] must be the WebView's box.
 *
 * @param menu The highlights under the tap, and which one the menu shows.
 * @param theme The reading theme, which sets the menu's colours.
 * @param onStep Moves the active highlight by one place: -1 for previous, 1 for next.
 * @param onColor A colour was chosen for the active highlight; the checked one too.
 * @param onCopy Copy was chosen, with the active highlight's text.
 * @param onDelete Delete was chosen for the active highlight.
 * @param onDismiss A touch outside the menu was lifted.
 */
@Composable
internal fun ReaderHighlightMenuOverlay(
    menu: HighlightMenu,
    theme: ReaderTheme,
    onStep: (delta: Int) -> Unit,
    onColor: (HighlightColor) -> Unit,
    onCopy: (text: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = readerChromeColors(theme)
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    BoxWithConstraints(modifier) {
        // The scrim comes before the menu, so the menu is hit first and its touches never reach the scrim.
        Box(
            Modifier
                .fillMaxSize()
                .testTag(ReaderHighlightMenuTags.SCRIM)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false).consume()
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        currentOnDismiss()
                    }
                },
        )
        if (menu.hasSwitcher) {
            menu.activeEntry.rects.forEach { rect ->
                Box(
                    Modifier
                        .offset((rect.x - 2).toFloat().dp, (rect.y - 1).toFloat().dp)
                        .size((rect.width + 4).toFloat().dp, (rect.height + 2).toFloat().dp)
                        .border(1.5.dp, colors.ink, RoundedCornerShape(3.5.dp))
                        .clearAndSetSemantics { testTag = ReaderHighlightMenuTags.OUTLINE },
                )
            }
        }
        val entry = menu.activeEntry
        highlightMenuPlacement(
            rects = entry.rects,
            size = highlightMenuSize(hasCopy = entry.canCopy, hasSwitcher = menu.hasSwitcher),
            boundsWidth = maxWidth.value,
            boundsHeight = maxHeight.value,
        )?.let { placement ->
            ReaderHighlightMenu(menu, placement, colors, onStep, onColor, onCopy, onDelete)
        }
    }
}

/**
 * The menu's body and tail, with the "N of M" row above the toolbar when [menu] has a switcher. The toolbar holds the
 * five colours, which recolour the active highlight, with a check on the active one, Copy when the active highlight has
 * text, and Delete.
 */
@Composable
private fun ReaderHighlightMenu(
    menu: HighlightMenu,
    placement: HighlightMenuPlacement,
    colors: ReaderChromeColors,
    onStep: (delta: Int) -> Unit,
    onColor: (HighlightColor) -> Unit,
    onCopy: (text: String) -> Unit,
    onDelete: () -> Unit,
) {
    val m = HighlightMenuMetrics
    val entry = menu.activeEntry
    val shape = SelectionMenuShape(
        tailUp = placement.tailUp,
        tailCenterX = placement.tailCenterX.dp,
        corner = m.CORNER.dp,
    )
    val tailPadding = m.TAIL_HEIGHT.dp
    Box(
        Modifier
            .offset(placement.left.dp, placement.top.dp)
            .size(placement.width.dp, placement.height.dp)
            .testTag(ReaderHighlightMenuTags.MENU)
            // A touch on the menu is the menu's, so the scrim beneath never sees it (the menu is hit first).
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        // The shadow goes on the body alone, since a tail outline casts none before API 29.
        Box(
            Modifier
                .fillMaxWidth()
                .height((placement.height - m.TAIL_HEIGHT).dp)
                .align(if (placement.tailUp) Alignment.BottomStart else Alignment.TopStart)
                .shadow(8.dp, RoundedCornerShape(m.CORNER.dp)),
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
                if (menu.hasSwitcher) {
                    SwitcherRow(menu, colors, onStep)
                    HorizontalDivider(thickness = m.RULE.dp, color = colors.border)
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(m.ROW_HEIGHT.dp)
                        .padding(horizontal = m.PADDING.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HighlightColor.entries.forEach { color ->
                        val label = stringResource(color.labelRes)
                        val selected = color == entry.color
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .selectable(selected = selected, role = Role.Button, onClick = { onColor(color) })
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .background(color.tint(), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }
                    Box(Modifier.padding(horizontal = 6.dp)) {
                        Box(Modifier.size(width = 1.dp, height = 24.dp).background(colors.border))
                    }
                    entry.text?.takeIf { entry.canCopy }?.let { text ->
                        val copyLabel = stringResource(R.string.reader_selection_copy)
                        MenuTooltip(copyLabel) {
                            Box(
                                Modifier
                                    .size(m.SLOT.dp)
                                    .clickable(role = Role.Button) { onCopy(text) }
                                    .semantics { contentDescription = copyLabel },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_reader_copy),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = colors.ink,
                                )
                            }
                        }
                    }
                    val deleteLabel = stringResource(R.string.reader_highlight_delete)
                    MenuTooltip(deleteLabel) {
                        Box(
                            Modifier
                                .size(m.SLOT.dp)
                                .testTag(ReaderHighlightMenuTags.DELETE)
                                .clickable(role = Role.Button, onClick = onDelete)
                                .semantics { contentDescription = deleteLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_reader_delete),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = colors.danger,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The switcher row: previous, a dot in the active colour with "N of M", and next. Both arrows are always enabled,
 * because stepping wraps around.
 */
@Composable
private fun SwitcherRow(menu: HighlightMenu, colors: ReaderChromeColors, onStep: (delta: Int) -> Unit) {
    val previous = stringResource(R.string.reader_highlight_previous)
    val next = stringResource(R.string.reader_highlight_next)
    val position = stringResource(R.string.reader_highlight_position, menu.position, menu.count)
    val positionA11y = stringResource(R.string.reader_highlight_position_a11y, menu.position, menu.count)
    Row(
        Modifier
            .fillMaxWidth()
            .height(HighlightMenuMetrics.SWITCHER_HEIGHT.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MenuTooltip(previous) {
            ArrowButton(R.drawable.ic_reader_chevron_left, previous, colors) { onStep(-1) }
        }
        Row(
            Modifier.clearAndSetSemantics {
                contentDescription = positionA11y
                liveRegion = LiveRegionMode.Polite
                testTag = ReaderHighlightMenuTags.POSITION
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(8.dp).background(menu.activeEntry.color.tint(), CircleShape))
            Text(
                text = position,
                fontFamily = UiFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colors.ink,
            )
        }
        MenuTooltip(next) {
            ArrowButton(R.drawable.ic_reader_chevron_right, next, colors) { onStep(1) }
        }
    }
}

/** A 44 dp arrow that calls [onClick] when tapped, with [label] as its name. */
@Composable
private fun ArrowButton(icon: Int, label: String, colors: ReaderChromeColors, onClick: () -> Unit) {
    Box(
        Modifier
            .size(HighlightMenuMetrics.SLOT.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.ink,
        )
    }
}

/** Shows [label] above [content] on a long press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MenuTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}
