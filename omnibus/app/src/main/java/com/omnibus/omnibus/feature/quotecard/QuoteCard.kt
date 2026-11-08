package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.ui.theme.DisplayFontFamily
import com.omnibus.omnibus.ui.theme.MonoFontFamily
import com.omnibus.omnibus.ui.theme.UiFontFamily
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

private const val PADDING = 0.094f
private const val LINE_HEIGHT_EM = 1.55f
private const val RULE_WIDTH = 0.14f
private const val RULE_GAP = 0.042f
private const val AUTHOR_GAP = 0.012f
private const val MARK_SIZE = 0.30f
private const val MARK_HEIGHT = 0.10f
private const val MARK_LEFT = -0.035f

/** How far a curly quote's bottom sits above its baseline, as a share of the font size. Real faces run 0.42 to 0.50. */
private const val QUOTE_RISE_EM = 0.42f

/** Every metric of a [QuoteCard], as a fraction of the card's [width] in pixels. */
private class CardMetrics(val width: Float, density: Density) : Density by density {
    fun px(fraction: Float) = width * fraction
    fun dp(fraction: Float): Dp = px(fraction).toDp()
    fun sp(fraction: Float): TextUnit = px(fraction).toSp()

    /** The rule is at least one device pixel thick, so it survives a small preview. */
    fun ruleHeight(): Dp = max(1f, px(0.004f)).toDp()
}

/**
 * A passage on a painted card, laid out the same at any width: every metric is a fraction of the incoming width, and
 * text sizes ignore the user's font scale, so the preview and the exported PNG are one picture. The width must be
 * bounded. A passage too long for [shape] ends in an ellipsis rather than overflowing.
 */
@Composable
fun QuoteCard(
    passage: String,
    title: String,
    author: String?,
    fill: QuoteFill,
    typeface: QuoteTypeface,
    size: QuoteSize,
    bold: Boolean,
    italic: Boolean,
    shape: QuoteShape,
    modifier: Modifier = Modifier,
) {
    // The card ignores the system font scale. Under non-linear scaling sp and px no longer convert one to one.
    val density = LocalDensity.current
    val unscaled = remember(density.density) { Density(density.density, fontScale = 1f) }
    CompositionLocalProvider(LocalDensity provides unscaled) {
        BoxWithConstraints(
            modifier
                .aspectRatio(shape.ratio)
                .clipToBounds()
                .drawBehind {
                    val (start, end) = gradientEndpoints(fill.angle, this.size)
                    drawRect(Brush.linearGradient(listOf(fill.from, fill.to), start = start, end = end))
                },
        ) {
            val metrics = CardMetrics(constraints.maxWidth.toFloat(), unscaled)
            val text = remember(passage) { passage.collapsingWhitespace() }
            val fontPx = metrics.width * passageSizeFraction(text) * size.scale
            val passageStyle = metrics.passageStyle(fontPx, fill, typeface, bold, italic)
            Column(Modifier.fillMaxSize().padding(metrics.dp(PADDING))) {
                PassageArea(text, passageStyle, fontPx, metrics, Modifier.weight(1f).fillMaxWidth())
                Footer(title, author, fill, metrics)
            }
        }
    }
}

/**
 * The passage face for [typeface] at [fontPx], with the toggles applied. Bold is the heaviest cut bundled. The line height
 * is in em, not sp: an sp height is converted on its own curve under non-linear font scaling and would drift from the budget.
 */
private fun CardMetrics.passageStyle(
    fontPx: Float,
    fill: QuoteFill,
    typeface: QuoteTypeface,
    bold: Boolean,
    italic: Boolean,
): TextStyle {
    return TextStyle(
        color = fill.ink,
        fontFamily = when (typeface) {
            QuoteTypeface.Serif -> DisplayFontFamily
            QuoteTypeface.Sans -> UiFontFamily
            QuoteTypeface.Mono -> MonoFontFamily
        },
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontSize = fontPx.toSp(),
        lineHeight = LINE_HEIGHT_EM.em,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** The passage and the quote mark above it, centred as one group, with as many lines as the area holds. */
@Composable
private fun PassageArea(text: String, style: TextStyle, fontPx: Float, metrics: CardMetrics, modifier: Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val lineHeightPx = fontPx * LINE_HEIGHT_EM
        val markHeight = metrics.px(MARK_HEIGHT)
        val lines = floor((constraints.maxHeight - markHeight) / lineHeightPx).toInt().coerceAtLeast(1)
        Column(Modifier.fillMaxWidth()) {
            QuoteMark(style, metrics)
            Text(text, style = style, maxLines = lines, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * The oversized opening quote, in the passage face. Its box is [MARK_HEIGHT] tall with the glyph's bottom on the box's
 * bottom edge, so the first line below it never touches it.
 */
@Composable
private fun QuoteMark(passageStyle: TextStyle, metrics: CardMetrics) {
    val fontSize = metrics.px(MARK_SIZE)
    Text(
        text = "“",
        style = passageStyle.copy(
            color = passageStyle.color.copy(alpha = 0.13f),
            fontSize = metrics.sp(MARK_SIZE),
            lineHeight = TextUnit.Unspecified,
            lineHeightStyle = null,
        ),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.clearAndSetSemantics {}.layout { measurable, _ ->
            val placeable = measurable.measure(Constraints())
            val boxHeight = metrics.px(MARK_HEIGHT).roundToInt()
            val top = boxHeight - (placeable[FirstBaseline] - QUOTE_RISE_EM * fontSize).roundToInt()
            layout(placeable.width, boxHeight) { placeable.place(metrics.px(MARK_LEFT).roundToInt(), top) }
        },
    )
}

/** The rule, the author, the title and, trailing, the OMNIBUS signature. The title wraps clear of the signature. */
@Composable
private fun Footer(title: String, author: String?, fill: QuoteFill, metrics: CardMetrics) {
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Spacer(
                Modifier
                    .width(metrics.dp(RULE_WIDTH))
                    .height(metrics.ruleHeight())
                    .background(fill.ink.copy(alpha = 0.22f)),
            )
            Spacer(Modifier.height(metrics.dp(RULE_GAP)))
            if (!author.isNullOrBlank()) {
                Text(
                    text = author.uppercase(),
                    style = signature(metrics, 0.026f, 0.0036f, fill.ink.copy(alpha = 0.75f)),
                    modifier = Modifier.padding(bottom = metrics.dp(AUTHOR_GAP)),
                )
            }
            Text(
                text = title,
                style = TextStyle(
                    color = fill.ink.copy(alpha = 0.62f),
                    fontFamily = DisplayFontFamily,
                    fontStyle = FontStyle.Italic,
                    fontSize = metrics.sp(0.033f),
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "OMNIBUS",
            style = signature(metrics, 0.021f, 0.006f, fill.ink.copy(alpha = 0.34f)),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Small semibold capitals in the UI face. */
private fun signature(metrics: CardMetrics, size: Float, tracking: Float, color: Color) =
    TextStyle(
        color = color,
        fontFamily = UiFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = metrics.sp(size),
        letterSpacing = metrics.sp(tracking),
    )
