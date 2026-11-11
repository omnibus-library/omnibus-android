package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Test tags for the settings sheet's controls. */
internal object ReaderSettingsSheetTags {
    const val SHEET = "reader_settings_sheet"
    const val DONE = "reader_settings_done"
    const val AA_PREVIEW = "reader_settings_aa"
    const val SIZE_VALUE = "reader_settings_size_value"
    const val SIZE_DECREASE = "reader_settings_size_decrease"
    const val SIZE_INCREASE = "reader_settings_size_increase"
    const val LINE_HEIGHT_VALUE = "reader_settings_line_height_value"
    const val LINE_HEIGHT_DECREASE = "reader_settings_line_height_decrease"
    const val LINE_HEIGHT_INCREASE = "reader_settings_line_height_increase"
    const val JUSTIFY = "reader_settings_justify"

    fun swatch(theme: ReaderTheme) = "reader_settings_swatch_${theme.token}"
    fun typeface(face: ReaderTypeface) = "reader_settings_typeface_${face.token}"
    fun margins(margins: ReaderMargins) = "reader_settings_margins_${margins.token}"
    fun pageTurn(mode: ReaderPageTurn) = "reader_settings_page_turn_${mode.token}"
}

/** Presentation constants for the settings sheet. */
internal object ReaderSettingsSheetDefaults {
    /** No dimming, so the reader can judge a change on the live page behind the sheet. */
    val ScrimColor = Color.Transparent

    /** The share of the window the sheet takes when open. */
    const val ExpandedHeightFraction = 0.92f

    /** The share of the window the sheet takes at its lower stop. Dragged below it, the sheet closes. */
    const val CollapsedHeightFraction = 0.40f
}

/** How far past the lower stop a drag must end to close the sheet, as Material's sheets use. */
private val CloseDragThreshold = 56.dp

/** How fast a release must be to move to the next stop rather than the nearest, as Material's sheets use. */
private val StopVelocityThreshold = 125.dp

/**
 * The share of the window the sheet shows, between [ReaderSettingsSheetDefaults.CollapsedHeightFraction]
 * and [ReaderSettingsSheetDefaults.ExpandedHeightFraction] at rest, lower while being dragged closed.
 * It depends only on the gestures and the window height, never on the sheet's own position or
 * insets, so the sheet's height and its place cannot feed back into each other.
 */
private class SettingsSheetHeight(private val scope: CoroutineScope, private val onClose: () -> Unit) {
    var fraction by mutableFloatStateOf(ReaderSettingsSheetDefaults.ExpandedHeightFraction)
        private set
    var windowHeightPx = 0f
    var closeThresholdPx = 0f
    var velocityThresholdPx = 0f
    private var settling: Job? = null

    private val expanded get() = ReaderSettingsSheetDefaults.ExpandedHeightFraction
    private val collapsed get() = ReaderSettingsSheetDefaults.CollapsedHeightFraction

    val isAtStop get() = fraction == expanded || fraction == collapsed
    val isExpanded get() = fraction == expanded

    /** Moves the sheet's top by [deltaPx] (down is positive) and returns the part it used. */
    fun dragBy(deltaPx: Float): Float {
        if (windowHeightPx <= 0f) return 0f
        settling?.cancel()
        val old = fraction
        fraction = (old - deltaPx / windowHeightPx).coerceIn(0f, expanded)
        return (old - fraction) * windowHeightPx
    }

    /** Settles at a stop after a release at [velocity] px/s (down is positive), or closes the sheet. */
    fun settle(velocity: Float) {
        val closeFraction = if (windowHeightPx > 0f) collapsed - closeThresholdPx / windowHeightPx else collapsed
        val target = when {
            fraction < closeFraction -> null
            velocity > velocityThresholdPx -> if (fraction > collapsed) collapsed else null
            velocity < -velocityThresholdPx -> expanded
            fraction >= (expanded + collapsed) / 2 -> expanded
            else -> collapsed
        }
        if (target == null) {
            onClose()
            return
        }
        settling?.cancel()
        settling = scope.launch {
            animate(fraction, target, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { value, _ ->
                fraction = value
            }
        }
    }

    /**
     * Passes the controls' scroll to the sheet: a drag up opens the sheet fully before the controls
     * scroll, and a drag down past the top of the controls lowers the sheet.
     */
    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (available.y < 0 && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero

        override suspend fun onPreFling(available: Velocity): Velocity =
            if (available.y < 0 && !isExpanded) {
                settle(available.y)
                available
            } else {
                Velocity.Zero
            }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
            if (!isAtStop) {
                settle(available.y)
                available
            } else {
                Velocity.Zero
            }
    }
}

/**
 * The reader's settings sheet: page theme, typeface, size, line height, margins, justification and page turn.
 * Stateless apart from its sheet state. Each control reports the new settings through
 * [onSettingsChanged], and the caller owns the value. The sheet takes the app theme, never the
 * reading theme, so it is composed inside `OmnibusTheme()`.
 *
 * @param accent the open book's accent, for the selected states and the justify switch.
 * @param onDismiss called when the sheet is closed with Done, a swipe, or a tap outside it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheet(
    settings: ReaderSettings,
    accent: BookAccent,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    val currentClose by rememberUpdatedState(close)
    val height = remember(scope) { SettingsSheetHeight(scope, onClose = { currentClose() }) }
    val density = LocalDensity.current
    height.closeThresholdPx = with(density) { CloseDragThreshold.toPx() }
    height.velocityThresholdPx = with(density) { StopVelocityThreshold.toPx() }

    // Material's sheet has no stop at 40% and sizes itself to its content, so the sheet here keeps
    // only its window, scrim, back handling and slide in and out. Its gestures are off and it takes
    // the height [SettingsSheetHeight] gives it, so its open position is always that height's top.
    // The window insets are left to the body: Material's top inset shrinks with the sheet's offset,
    // and a body sized from what is left made the sheet's height and offset chase each other.
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        shape = shape,
        containerColor = Color.Transparent,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = ReaderSettingsSheetDefaults.ScrimColor,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    height.windowHeightPx = constraints.maxHeight.toFloat()
                    val visible = (constraints.maxHeight * height.fraction).roundToInt()
                    // Below the lower stop the sheet keeps that height and slides down instead.
                    val sheetHeight = maxOf(
                        visible,
                        (constraints.maxHeight * ReaderSettingsSheetDefaults.CollapsedHeightFraction).roundToInt(),
                    ).coerceAtMost(constraints.maxHeight)
                    val placeable = measurable.measure(
                        constraints.copy(minHeight = sheetHeight, maxHeight = sheetHeight),
                    )
                    layout(placeable.width, placeable.height) { placeable.place(0, sheetHeight - visible) }
                }
                .border(0.5.dp, palette.line, shape)
                .background(palette.bg0, shape)
                .nestedScroll(height.nestedScrollConnection)
                .draggable(
                    state = rememberDraggableState { height.dragBy(it) },
                    orientation = Orientation.Vertical,
                    onDragStopped = { height.settle(it) },
                )
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            BottomSheetDefaults.DragHandle(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = palette.line2,
            )
            ReaderSettingsSheetContent(
                settings = settings,
                accent = accent,
                onSettingsChanged = onSettingsChanged,
                onDone = close,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The sheet's body, without the sheet, so it can be previewed. */
@Composable
internal fun ReaderSettingsSheetContent(
    settings: ReaderSettings,
    accent: BookAccent,
    onSettingsChanged: (ReaderSettings) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    // The header stays put; only the controls under it scroll.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(ReaderSettingsSheetTags.SHEET)
            .background(palette.bg0)
            .padding(horizontal = Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        SheetHeader(onDone = onDone)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            PageGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
            TypeGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
            LayoutGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
            PageTurnGroup(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
        }
    }
}

@Composable
private fun SheetHeader(onDone: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.reader_settings_title),
            modifier = Modifier.weight(1f),
            style = displayTextStyle(28, palette.ink0),
        )
        TextButton(
            onClick = onDone,
            modifier = Modifier.testTag(ReaderSettingsSheetTags.DONE).heightIn(min = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
        ) {
            Text(
                text = stringResource(R.string.reader_settings_done),
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            )
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    val palette = OmnibusTheme.palette
    Text(
        text = text.uppercase(),
        style = uiTextStyle(11, FontWeight.SemiBold, palette.ink3).copy(letterSpacing = 1.sp),
    )
}

@Composable
private fun PageGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_page))
        Row(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            listOf(ReaderTheme.Light, ReaderTheme.Sepia, ReaderTheme.Dark, ReaderTheme.Black).forEach { theme ->
                ThemeSwatch(
                    theme = theme,
                    selected = settings.theme == theme,
                    accent = accent,
                    onClick = { if (settings.theme != theme) onSettingsChanged(settings.copy(theme = theme)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A page in [theme]'s ground with four text-line bars in its ink, labelled beneath. */
@Composable
private fun ThemeSwatch(
    theme: ReaderTheme,
    selected: Boolean,
    accent: BookAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val ink = readerSwatchInk(theme)
    val pageShape = RoundedCornerShape(Radius.md)
    Column(
        modifier = modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(ReaderSettingsSheetTags.swatch(theme)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.82f)
                .clip(pageShape)
                .background(Color(theme.groundArgb))
                .border(
                    width = if (selected) 2.dp else 0.75.dp,
                    color = if (selected) accent.tint else palette.line,
                    shape = pageShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.5.dp)) {
                listOf(1.0f, 0.86f, 0.94f, 0.6f).forEach { fraction ->
                    Box(
                        Modifier
                            .width(30.dp * fraction)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(ink.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        Text(
            text = stringResource(swatchLabel(theme)),
            style = uiTextStyle(
                11,
                if (selected) FontWeight.SemiBold else FontWeight.Normal,
                if (selected) palette.ink0 else palette.ink3,
            ),
        )
    }
}

@Composable
private fun TypeGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    val faces = ReaderTypeface.entries
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_type))
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            faces.chunked(3).forEach { rowFaces ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowFaces.forEach { face ->
                        TypefaceCapsule(
                            face = face,
                            selected = settings.typeface == face,
                            accent = accent,
                            onClick = {
                                if (settings.typeface != face) onSettingsChanged(settings.copy(typeface = face))
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(palette.bg1)
                .border(0.5.dp, palette.line, RoundedCornerShape(Radius.lg)),
        ) {
            SizeRow(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.line2))
            LineHeightRow(settings = settings, accent = accent, onSettingsChanged = onSettingsChanged)
        }
    }
}

/** One typeface capsule, labelled in the UI face. Filled with the accent when selected. */
@Composable
private fun TypefaceCapsule(
    face: ReaderTypeface,
    selected: Boolean,
    accent: BookAccent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(ReaderSettingsSheetTags.typeface(face))
            .clip(CircleShape)
            .background(if (selected) accent.tint else palette.bg2.copy(alpha = 0.7f))
            .padding(vertical = 7.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(typefaceLabel(face)),
            style = uiTextStyle(
                13,
                if (selected) FontWeight.SemiBold else FontWeight.Medium,
                if (selected) accent.onTint else palette.ink2,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SizeRow(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_settings_size),
            modifier = Modifier.weight(1f),
            style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
        )
        // The tag sits on the box, so the decorative text can clear its own semantics without losing it.
        Box(
            Modifier
                .width(56.dp)
                .testTag(ReaderSettingsSheetTags.AA_PREVIEW),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = stringResource(R.string.reader_settings_aa),
                modifier = Modifier.clearAndSetSemantics {},
                style = displayTextStyle(settings.fontSize, palette.ink1),
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Text(
            text = settings.fontSize.toString(),
            modifier = Modifier.testTag(ReaderSettingsSheetTags.SIZE_VALUE).semantics {
                stateDescription = "${settings.fontSize} point"
            },
            style = monoTextStyle(12, palette.ink2),
        )
        Stepper(
            iconRes = R.drawable.ic_reader_minus,
            accent = accent,
            description = stringResource(R.string.reader_settings_size_decrease),
            enabled = settings.canDecreaseFontSize,
            onClick = { onSettingsChanged(settings.withFontSizeStep(-1)) },
            tag = ReaderSettingsSheetTags.SIZE_DECREASE,
        )
        Stepper(
            iconRes = R.drawable.ic_reader_plus,
            accent = accent,
            description = stringResource(R.string.reader_settings_size_increase),
            enabled = settings.canIncreaseFontSize,
            onClick = { onSettingsChanged(settings.withFontSizeStep(+1)) },
            tag = ReaderSettingsSheetTags.SIZE_INCREASE,
        )
    }
}

@Composable
private fun LineHeightRow(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.reader_settings_line_height),
            modifier = Modifier.weight(1f),
            style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
        )
        Text(
            // Locale.ROOT keeps the decimal point, so 1.6 never reads as "1,6".
            text = String.format(Locale.ROOT, "%.1f", settings.lineHeight),
            modifier = Modifier.testTag(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE),
            style = monoTextStyle(12, palette.ink2),
        )
        Stepper(
            iconRes = R.drawable.ic_reader_minus,
            accent = accent,
            description = stringResource(R.string.reader_settings_line_height_decrease),
            enabled = settings.canDecreaseLineHeight,
            onClick = { onSettingsChanged(settings.withLineHeightStep(-1)) },
            tag = ReaderSettingsSheetTags.LINE_HEIGHT_DECREASE,
        )
        Stepper(
            iconRes = R.drawable.ic_reader_plus,
            accent = accent,
            description = stringResource(R.string.reader_settings_line_height_increase),
            enabled = settings.canIncreaseLineHeight,
            onClick = { onSettingsChanged(settings.withLineHeightStep(+1)) },
            tag = ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE,
        )
    }
}

/**
 * A round minus or plus. The touch target is 48dp around a 32dp circle. The glyph is the book's accent
 * on the dark app theme and the ink on the light one, and muted when disabled.
 */
@Composable
private fun Stepper(
    iconRes: Int,
    accent: BookAccent,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    val palette = OmnibusTheme.palette
    val tint = when {
        !enabled -> palette.ink3
        palette == OmnibusPalette.Atrium -> accent.tint
        else -> palette.ink0
    }
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(palette.bg2, CircleShape)
                .border(0.5.dp, palette.line2, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun LayoutGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.lg)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_layout))
        SegmentedPill(
            options = ReaderMargins.entries,
            selected = settings.margins,
            accent = accent,
            label = { stringResource(marginsLabel(it)) },
            tag = ReaderSettingsSheetTags::margins,
            onSelect = { onSettingsChanged(settings.copy(margins = it)) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.bg1)
                .border(0.5.dp, palette.line, shape)
                .toggleable(
                    value = settings.justify,
                    role = Role.Switch,
                    onValueChange = { onSettingsChanged(settings.copy(justify = it)) },
                )
                .testTag(ReaderSettingsSheetTags.JUSTIFY)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.reader_settings_justify),
                modifier = Modifier.weight(1f),
                style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
            )
            Switch(
                checked = settings.justify,
                onCheckedChange = null,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent.tint,
                    checkedThumbColor = accent.onTint,
                ),
            )
        }
    }
}

@Composable
private fun PageTurnGroup(settings: ReaderSettings, accent: BookAccent, onSettingsChanged: (ReaderSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        GroupLabel(stringResource(R.string.reader_settings_group_page_turn))
        SegmentedPill(
            options = ReaderPageTurn.entries,
            selected = settings.pageTurn,
            accent = accent,
            label = { stringResource(pageTurnLabel(it)) },
            tag = ReaderSettingsSheetTags::pageTurn,
            onSelect = { onSettingsChanged(settings.copy(pageTurn = it)) },
        )
    }
}

/** A row of mutually exclusive choices in a pill, the selected one in the book's accent. */
@Composable
private fun <T> SegmentedPill(
    options: List<T>,
    selected: T,
    accent: BookAccent,
    label: @Composable (T) -> String,
    tag: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .selectableGroup()
            .clip(CircleShape)
            .background(palette.bg2.copy(alpha = 0.7f))
            .padding(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = {
                            if (!isSelected) onSelect(option)
                        },
                    )
                    .testTag(tag(option))
                    .clip(CircleShape)
                    .background(if (isSelected) accent.tint else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = uiTextStyle(
                        14,
                        if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        if (isSelected) accent.onTint else palette.ink2,
                    ),
                )
            }
        }
    }
}

private fun swatchLabel(theme: ReaderTheme): Int = when (theme) {
    ReaderTheme.Light -> R.string.reader_settings_theme_light
    ReaderTheme.Sepia -> R.string.reader_settings_theme_sepia
    ReaderTheme.Dark -> R.string.reader_settings_theme_dark
    ReaderTheme.Black -> R.string.reader_settings_theme_black
}

private fun typefaceLabel(face: ReaderTypeface): Int = when (face) {
    ReaderTypeface.Original -> R.string.reader_settings_typeface_original
    ReaderTypeface.Editorial -> R.string.reader_settings_typeface_editorial
    ReaderTypeface.Classic -> R.string.reader_settings_typeface_classic
    ReaderTypeface.Modern -> R.string.reader_settings_typeface_modern
    ReaderTypeface.Sans -> R.string.reader_settings_typeface_sans
    ReaderTypeface.Mono -> R.string.reader_settings_typeface_mono
}

private fun marginsLabel(margins: ReaderMargins): Int = when (margins) {
    ReaderMargins.Narrow -> R.string.reader_settings_margins_narrow
    ReaderMargins.Normal -> R.string.reader_settings_margins_normal
    ReaderMargins.Wide -> R.string.reader_settings_margins_wide
}

private fun pageTurnLabel(mode: ReaderPageTurn): Int = when (mode) {
    ReaderPageTurn.Slide -> R.string.reader_settings_page_turn_slide
    ReaderPageTurn.Curl -> R.string.reader_settings_page_turn_curl
    ReaderPageTurn.None -> R.string.reader_settings_page_turn_none
}

@PreviewLightDark
@Composable
private fun ReaderSettingsSheetPreview() {
    OmnibusTheme {
        ReaderSettingsSheetContent(
            settings = ReaderSettings(),
            accent = readerSheetAccent(ReaderBookState.Ready(title = "The Long Road Home"), OmnibusTheme.palette),
            onSettingsChanged = {},
            onDone = {},
        )
    }
}
