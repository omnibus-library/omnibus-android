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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.key
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
import com.omnibus.omnibus.feature.reader.HighlightMenuAction
import com.omnibus.omnibus.feature.reader.HighlightMenuTarget
import com.omnibus.omnibus.feature.reader.menuTarget
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

    /** The test tag of [action]'s button in the action row. */
    fun action(action: HighlightMenuAction): String = "reader_highlight_" + action.name.lowercase()
}

/**
 * The highlight menu over the page, with the outline of the active highlight when several are under the tap, and a
 * scrim that takes every touch outside the menu. Any touch on the scrim calls [onDismiss] once it lifts, and never
 * reaches the page beneath. All coordinates are the page's dp, so [modifier] must be the WebView's box.
 *
 * @param menu The highlights under the tap, and which one the menu shows.
 * @param actions The actions the second row shows, in order.
 * @param theme The reading theme, which sets the menu's colours.
 * @param onStep Moves the active highlight by one place: -1 for previous, 1 for next.
 * @param onColor A colour was chosen for the active highlight; the checked one too.
 * @param onAction An action was chosen for the active highlight.
 * @param onDismiss A touch outside the menu was lifted.
 */
@Composable
internal fun ReaderHighlightMenuOverlay(
    menu: HighlightMenu,
    actions: List<HighlightMenuAction>,
    theme: ReaderTheme,
    onStep: (delta: Int) -> Unit,
    onColor: (HighlightColor) -> Unit,
    onAction: (HighlightMenuAction) -> Unit,
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
            size = highlightMenuSize(hasSwitcher = menu.hasSwitcher),
            boundsWidth = maxWidth.value,
            boundsHeight = maxHeight.value,
        )?.let { placement ->
            ReaderHighlightMenu(menu, placement, colors, actions, onStep, onColor, onAction)
        }
    }
}

/**
 * The menu's body and tail: the "N of M" row when [menu] has a switcher, the five colours with a check on the active
 * one, a rule, and the action row of [actions] for the active highlight.
 */
@Composable
private fun ReaderHighlightMenu(
    menu: HighlightMenu,
    placement: HighlightMenuPlacement,
    colors: ReaderChromeColors,
    actions: List<HighlightMenuAction>,
    onStep: (delta: Int) -> Unit,
    onColor: (HighlightColor) -> Unit,
    onAction: (HighlightMenuAction) -> Unit,
) {
    val m = HighlightMenuMetrics
    val entry = menu.activeEntry
    HighlightMenuSurface(
        left = placement.left,
        top = placement.top,
        width = placement.width,
        height = placement.height,
        tailUp = placement.tailUp,
        tailCenterX = placement.tailCenterX,
        colors = colors,
        // A touch on the menu is the menu's, so the scrim beneath never sees it (the menu is hit first).
        modifier = Modifier
            .testTag(ReaderHighlightMenuTags.MENU)
            .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
    ) {
        if (menu.hasSwitcher) {
            SwitcherRow(menu, colors, onStep)
            HorizontalDivider(thickness = m.RULE.dp, color = colors.border)
        }
        HighlightMenuColorRow(selected = entry.color, onColor = onColor)
        HorizontalDivider(thickness = m.RULE.dp, color = colors.border)
        HighlightMenuActionRow(actions = actions, target = entry.menuTarget, colors = colors, onAction = onAction)
    }
}

/**
 * The floating plate that both the highlight menu and the selection menu sit on: a rounded body with a tail, at [left]
 * and [top] with the given [width] and [height] in the page's dp. The tail points up when [tailUp] (the menu is below
 * its anchor), centred [tailCenterX] from the left edge. [content] fills the body, clear of the tail. [modifier] is
 * applied to the plate, after its position and size.
 */
@Composable
internal fun HighlightMenuSurface(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    tailUp: Boolean,
    tailCenterX: Float,
    colors: ReaderChromeColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val m = HighlightMenuMetrics
    val shape = SelectionMenuShape(tailUp = tailUp, tailCenterX = tailCenterX.dp, corner = m.CORNER.dp)
    val tailPadding = m.TAIL_HEIGHT.dp
    Box(
        Modifier
            .offset(left.dp, top.dp)
            .size(width.dp, height.dp)
            .then(modifier),
    ) {
        // The shadow goes on the body alone, since a tail outline casts none before API 29.
        Box(
            Modifier
                .fillMaxWidth()
                .height((height - m.TAIL_HEIGHT).dp)
                .align(if (tailUp) Alignment.BottomStart else Alignment.TopStart)
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
                    .padding(top = if (tailUp) tailPadding else 0.dp, bottom = if (tailUp) 0.dp else tailPadding),
                content = content,
            )
        }
    }
}

/**
 * The colour row: the five colours in [HighlightColor] order, with a check on [selected] (none when it is null).
 * Choosing a colour calls [onColor], the checked one too.
 */
@Composable
internal fun HighlightMenuColorRow(selected: HighlightColor?, onColor: (HighlightColor) -> Unit) {
    val m = HighlightMenuMetrics
    Row(
        Modifier
            .fillMaxWidth()
            .height(m.ROW_HEIGHT.dp)
            .padding(horizontal = m.PADDING.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HighlightColor.entries.forEach { color ->
            val label = stringResource(color.labelRes)
            val isSelected = color == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .selectable(selected = isSelected, role = Role.Button, onClick = { onColor(color) })
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
                    if (isSelected) {
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
    }
}

/**
 * The action row: each of [actions] as an icon-only button in a [HighlightMenuMetrics.SLOT] slot, centred, named and
 * tooltipped by its label on [target]. Delete is drawn in the danger colour, the others in ink.
 */
@Composable
internal fun HighlightMenuActionRow(
    actions: List<HighlightMenuAction>,
    target: HighlightMenuTarget,
    colors: ReaderChromeColors,
    onAction: (HighlightMenuAction) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(HighlightMenuMetrics.ACTION_ROW_HEIGHT.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            key(action) {
                val label = stringResource(action.labelOn(target))
                MenuTooltip(label) {
                    Box(
                        Modifier
                            .size(HighlightMenuMetrics.SLOT.dp)
                            .testTag(ReaderHighlightMenuTags.action(action))
                            .clickable(role = Role.Button, onClick = { onAction(action) })
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(action.icon),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (action == HighlightMenuAction.Delete) colors.danger else colors.ink,
                        )
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
internal fun MenuTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
    ) {
        content()
    }
}
