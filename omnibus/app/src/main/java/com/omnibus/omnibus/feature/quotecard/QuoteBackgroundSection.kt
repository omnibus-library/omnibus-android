package com.omnibus.omnibus.feature.quotecard

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

private val SwatchSize = 28.dp
private val RingSize = 36.dp
private val SwatchCorner = 8.dp
private val RingCorner = 11.dp
private val CoralStop = Color(0xFFE07A5F)
private val TealStop = Color(0xFF2A9D8F)
private val FadeWidth = 24.dp

/**
 * The Background section: its heading with the pill that switches between the Solid and Gradient rows, and the row of
 * swatches that scrolls sideways, starting with the book's cover.
 */
@Composable
internal fun QuoteBackgroundSection(
    background: QuoteBackground,
    coverPairs: List<CoverPair>,
    book: CoverIdentity,
    onKindChosen: (QuoteBackgroundKind) -> Unit,
    onBackgroundChosen: (QuoteBackground) -> Unit,
    onCustomChosen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.quote_card_background),
                modifier = Modifier.weight(1f).semantics { heading() },
                style = displayTextStyle(23, palette.ink0),
            )
            KindPill(background.kind, onKindChosen)
        }
        SwatchRow(background, coverPairs, book, onBackgroundChosen, onCustomChosen)
    }
}

@StringRes
private fun QuoteBackgroundKind.label() = when (this) {
    QuoteBackgroundKind.Solid -> R.string.quote_card_kind_solid
    QuoteBackgroundKind.Gradient -> R.string.quote_card_kind_gradient
}

/** The card's current kind, which opens the menu that switches it. */
@Composable
private fun KindPill(kind: QuoteBackgroundKind, onKindChosen: (QuoteBackgroundKind) -> Unit) {
    val palette = OmnibusTheme.palette
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .testTag(QuoteCardTestTags.KIND_PILL)
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .background(palette.bg2)
                .clickable(role = Role.Button) { open = true }
                .padding(start = 14.dp, end = 10.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(kind.label()), style = uiTextStyle(14, FontWeight.SemiBold, palette.ink0))
            Icon(
                painter = painterResource(R.drawable.ic_audiobook_player_chevron_down),
                contentDescription = null,
                tint = palette.ink1,
                modifier = Modifier.padding(start = 2.dp).size(16.dp),
            )
        }
        if (open) {
            KindMenu(kind, onDismiss = { open = false }, onChosen = { open = false; onKindChosen(it) })
        }
    }
}

/** Places the menu under the pill, trailing edges together, and keeps it inside the window. */
private object BelowPill : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        x = (anchorBounds.right - popupContentSize.width).coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
        y = (anchorBounds.bottom + 4).coerceAtMost((windowSize.height - popupContentSize.height).coerceAtLeast(0)),
    )
}

/** A popup of the two kinds, each with its swatch shape and a check on the current one. Closes on a tap outside or back. */
@Composable
private fun KindMenu(current: QuoteBackgroundKind, onDismiss: () -> Unit, onChosen: (QuoteBackgroundKind) -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(14.dp)
    Popup(popupPositionProvider = BelowPill, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier
                .testTag(QuoteCardTestTags.KIND_MENU)
                .width(176.dp)
                .shadow(8.dp, shape)
                .clip(shape)
                .background(palette.bg1)
                .border(0.5.dp, palette.line, shape)
                .selectableGroup()
                .padding(vertical = 4.dp),
        ) {
            QuoteBackgroundKind.entries.forEach { kind ->
                Row(
                    Modifier
                        .testTag(QuoteCardTestTags.kindItem(kind))
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = kind == current, role = Role.RadioButton) { onChosen(kind) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(18.dp).clip(swatchShape(kind)).background(palette.ink2))
                    Text(
                        stringResource(kind.label()),
                        modifier = Modifier.weight(1f),
                        style = uiTextStyle(16, FontWeight.Medium, palette.ink0),
                    )
                    if (kind == current) {
                        Icon(painterResource(R.drawable.ic_check), null, tint = palette.ink0, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

/** Solid swatches are circles and gradient swatches are rounded squares, so a reader can tell the rows apart. */
private fun swatchShape(kind: QuoteBackgroundKind): Shape =
    if (kind == QuoteBackgroundKind.Solid) CircleShape else RoundedCornerShape(SwatchCorner)

/** The sideways row: the cover, then the swatches of the background's kind, faded where more lies beyond the edge. */
@Composable
private fun SwatchRow(
    background: QuoteBackground,
    coverPairs: List<CoverPair>,
    book: CoverIdentity,
    onBackgroundChosen: (QuoteBackground) -> Unit,
    onCustomChosen: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    val scroll = rememberScrollState()
    val kind = background.kind
    val isCustom = background is QuoteBackground.CustomSolid || background is QuoteBackground.CustomGradient
    Row(
        Modifier
            .testTag(QuoteCardTestTags.SWATCH_ROW)
            .fillMaxWidth()
            .edgeFade(scroll.canScrollBackward, scroll.canScrollForward)
            .horizontalScroll(scroll)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        BookCover(book, Modifier.padding(end = 8.dp).size(24.dp, 36.dp), size = CoverSize.Sm, cornerRadius = 3.dp)
        swatchesOf(kind, coverPairs).forEachIndexed { index, swatch ->
            Swatch(swatchLabel(swatch, coverNumber = index + 1), selected = swatch == background, kind = kind, onClick = { onBackgroundChosen(swatch) }) {
                SwatchFill(swatch, kind, book)
            }
            if (kind == QuoteBackgroundKind.Gradient && coverPairs.isNotEmpty() && index == coverPairs.lastIndex) {
                Box(Modifier.testTag(QuoteCardTestTags.DIVIDER).size(9.dp, 36.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(1.dp, 22.dp).background(palette.ink0.copy(alpha = 0.16f)))
                }
            }
        }
        val customLabel = if (kind == QuoteBackgroundKind.Solid) R.string.quote_card_swatch_custom_color else R.string.quote_card_swatch_custom_gradient
        Swatch(stringResource(customLabel), selected = isCustom, kind = kind, onClick = onCustomChosen) { CustomFill(kind) }
    }
}

@Composable
private fun swatchLabel(swatch: QuoteBackground, coverNumber: Int): String = when (swatch) {
    QuoteBackground.Book -> stringResource(R.string.quote_card_swatch_book)
    is QuoteBackground.Wash -> stringResource(
        when (swatch.wash) {
            QuoteWash.Ink -> R.string.quote_card_swatch_ink
            QuoteWash.Paper -> R.string.quote_card_swatch_paper
            QuoteWash.Moss -> R.string.quote_card_swatch_moss
            QuoteWash.Rose -> R.string.quote_card_swatch_rose
            QuoteWash.Dusk -> R.string.quote_card_swatch_dusk
            QuoteWash.Sea -> R.string.quote_card_swatch_sea
        },
    )
    is QuoteBackground.Cover -> stringResource(R.string.quote_card_swatch_cover, coverNumber)
    is QuoteBackground.Preset -> stringResource(
        when (swatch.preset) {
            QuoteGradientPreset.Ember -> R.string.quote_card_swatch_ember
            QuoteGradientPreset.Dusk -> R.string.quote_card_swatch_dusk
            QuoteGradientPreset.Lagoon -> R.string.quote_card_swatch_lagoon
            QuoteGradientPreset.Grove -> R.string.quote_card_swatch_grove
            QuoteGradientPreset.Dawn -> R.string.quote_card_swatch_dawn
        },
    )
    is QuoteBackground.CustomSolid -> stringResource(R.string.quote_card_swatch_custom_color)
    is QuoteBackground.CustomGradient -> stringResource(R.string.quote_card_swatch_custom_gradient)
}

/** One radio-button swatch: a 48 dp target around the swatch, and the accent ring when selected. */
@Composable
private fun Swatch(
    label: String,
    selected: Boolean,
    kind: QuoteBackgroundKind,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(SwatchSize)
                .clip(swatchShape(kind))
                .border(1.dp, palette.ink0.copy(alpha = 0.14f), swatchShape(kind)),
            contentAlignment = Alignment.Center,
            content = content,
        )
        if (selected) {
            val ring = if (kind == QuoteBackgroundKind.Solid) CircleShape else RoundedCornerShape(RingCorner)
            Box(Modifier.size(RingSize).border(2.dp, palette.accent, ring))
        }
    }
}

/** A swatch's colours: the flat base for a solid, the two stops on the diagonal for a gradient. */
@Composable
private fun BoxScope.SwatchFill(swatch: QuoteBackground, kind: QuoteBackgroundKind, book: CoverIdentity) {
    val fill = swatch.fill(book.tone)
    val brush = if (kind == QuoteBackgroundKind.Solid) SolidColor(fill.from) else Brush.linearGradient(listOf(fill.from, fill.to))
    Box(Modifier.matchParentSize().background(brush))
}

/** The Custom swatch: a rainbow for a solid, a coral and teal split for a gradient, with a plus. */
@Composable
private fun BoxScope.CustomFill(kind: QuoteBackgroundKind) {
    val brush = if (kind == QuoteBackgroundKind.Solid) {
        Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red))
    } else {
        Brush.linearGradient(0f to CoralStop, 0.5f to CoralStop, 0.5f to TealStop, 1f to TealStop)
    }
    Box(Modifier.matchParentSize().background(brush))
    Icon(painterResource(R.drawable.ic_reader_plus), null, tint = Color.White, modifier = Modifier.size(16.dp))
}

/** Fades out each edge that has more of the row beyond it, over an offscreen layer so only the row's own pixels fade. */
private fun Modifier.edgeFade(fadeStart: Boolean, fadeEnd: Boolean): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fade = FadeWidth.toPx() / size.width
        drawRect(
            brush = Brush.horizontalGradient(
                0f to if (fadeStart) Color.Transparent else Color.Black,
                fade to Color.Black,
                1f - fade to Color.Black,
                1f to if (fadeEnd) Color.Transparent else Color.Black,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
