package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The drawing's coordinate space. Everything below is laid out in these units, then scaled to fit. */
internal const val SCENE_WIDTH = 264f
internal const val SCENE_HEIGHT = 192f

/** Theme colors plus the wood the bookcase is made of, which the Atrium palette has no token for. */
internal class SceneColors(
    val wood: Color,
    val back: Color,
    val line: Color,
    val bg1: Color,
    val ink0: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val accent: Color,
)

internal fun sceneColors(palette: OmnibusPalette, isLight: Boolean) = SceneColors(
    wood = if (isLight) Color(0xFFA98663) else Color(0xFF54402E),
    back = if (isLight) Color(0xFFEFE7DC) else Color(0xFF17110C),
    line = palette.line,
    bg1 = palette.bg1,
    ink0 = palette.ink0,
    ink1 = palette.ink1,
    ink2 = palette.ink2,
    ink3 = palette.ink3,
    accent = palette.accent,
)

private val BookColors = listOf(
    Color(0xFFF0A556),
    Color(0xFFB5533C),
    Color(0xFF6E7F5C),
    Color(0xFF4F6D7A),
    Color(0xFF7B5A7E),
    Color(0xFFC9B99A),
    Color(0xFF8C6A45),
)
private val Skin = Color(0xFFE7BE9A)
private val Hair = Color(0xFF6B4630)
private val Trousers = Color(0xFF3F4A57)
private val Detail = Color(0xFF2A2420)
private val Collar = Color(0xFFF7F5F1)
private val SpineBand = Color.Black.copy(alpha = 0.22f)

private const val FLOOR = 186f
private const val BOARD_UPPER = 62f
private const val BOARD_MIDDLE = 112f
private const val BOARD_LOWER = 162f

/** The tab bar's You glyph (`ic_tab_you`), parsed so it stays sharp when scaled with the drawing. */
private val YouGlyph = PathParser().parsePathString(
    "M21,12a9,9 0 1 1 -18,0a9,9 0 1 1 18,0zM14.8,10a2.8,2.8 0 1 1 -5.6,0a2.8,2.8 0 1 1 5.6,0zM6.4,18.6a6,6 0 0 1 11.2,0",
).toPath()

private class Spine(val x: Float, val base: Float, val width: Float, val height: Float, val color: Color)

/**
 * A run of upright books from [fromX] to [toX]. The sizes come from a fixed sequence seeded by [seed], so a shelf looks
 * untidy but never changes between frames.
 */
private fun spines(fromX: Float, toX: Float, base: Float, seed: Int, maxHeight: Float = 38f): List<Spine> {
    var state = seed.toLong()
    fun next(): Float {
        state = (state * 9301 + 49297) % 233280
        return state / 233280f
    }
    var x = fromX
    var colorIndex = seed
    return buildList {
        while (x < toX - 5f) {
            val width = min(6f + (next() * 5f).roundToInt(), toX - x)
            val height = maxHeight - (next() * 11f).roundToInt()
            add(Spine(x, base, width, height, BookColors[colorIndex++ % BookColors.size]))
            x += width + 0.6f
        }
    }
}

private val SearchShelves = spines(26f, 240f, BOARD_UPPER, 3) +
    spines(26f, 146f, BOARD_MIDDLE, 11, 40f) +
    spines(26f, 104f, BOARD_LOWER, 7, 40f)
private val StatsShelf = spines(26f, 240f, BOARD_UPPER, 5)
private val YouShelves = spines(26f, 240f, BOARD_UPPER, 9) +
    spines(26f, 184f, BOARD_MIDDLE, 13, 40f) +
    spines(26f, 122f, BOARD_LOWER, 4, 40f)

/**
 * Draws [page]'s scene at [phase] of its loop, scaled to fill the canvas.
 *
 * @param reservedLabel The "reserved" tag on the You page's empty slot, measured in scene units.
 */
internal fun DrawScope.drawLibrarianScene(
    page: UnderConstructionPage,
    phase: Float,
    colors: SceneColors,
    reservedLabel: TextLayoutResult,
) {
    scale(size.width / SCENE_WIDTH, size.height / SCENE_HEIGHT, pivot = Offset.Zero) {
        drawLine(colors.line, Offset(4f, FLOOR), Offset(260f, FLOOR), strokeWidth = 1.5f)
        when (page) {
            UnderConstructionPage.Search -> drawSearchScene(phase, colors)
            UnderConstructionPage.Stats -> drawStatsScene(phase, colors)
            UnderConstructionPage.You -> drawYouScene(phase, colors, reservedLabel)
        }
    }
}

/** Up the ladder, sliding a book into the last gap while inspecting the shelf through a magnifying glass. */
private fun DrawScope.drawSearchScene(phase: Float, colors: SceneColors) {
    drawBookcase(colors, BOARD_UPPER, BOARD_MIDDLE, BOARD_LOWER)
    SearchShelves.forEach { drawSpine(it) }
    drawUpright(112f, BOARD_LOWER, 8f, 30f, BookColors[5])
    rotate(-16f, pivot = Offset(124f, BOARD_LOWER)) {
        roundRect(BookColors[4], 124f, 134f, 8f, 28f, 1.2f)
    }
    drawCart(34f, colors)
    drawLadder(colors)
    translate(left = 9f * (1f - shelvedAmount(phase))) {
        drawUpright(147f, BOARD_MIDDLE, 10f, 37f, BookColors[1])
    }
    drawLibrarian(174f, 130f, phase, colors, Arm(-11f, -44f, -20f, -37f))
    rotate(sin(phase * 2f * PI.toFloat()) * 7f, pivot = Offset(184f, 90f)) {
        limb(colors.accent, 185f, 88f, 199f, 83f, 5.5f)
        drawCircle(Skin, 2.8f, Offset(199f, 83f))
        limb(colors.ink1, 199f, 83f, 206f, 75f, 3f)
        drawCircle(BookColors[0].copy(alpha = 0.14f), 10.5f, Offset(213f, 67f))
        drawCircle(colors.ink0, 10.5f, Offset(213f, 67f), style = Stroke(2.5f))
    }
}

/** On a stool, topping up stacks of books that read as a bar chart. */
private fun DrawScope.drawStatsScene(phase: Float, colors: SceneColors) {
    drawBookcase(colors, BOARD_UPPER, BOARD_LOWER)
    StatsShelf.forEach { drawSpine(it) }
    drawBookStack(34f, BOARD_LOWER, 3, 1)
    drawBookStack(78f, BOARD_LOWER, 5, 3)
    drawBookStack(122f, BOARD_LOWER, 8, 5)
    translate(top = -8f * (1f - shelvedAmount(phase))) {
        drawLyingBook(122f, 91f, BookColors[0])
    }
    roundRect(colors.wood, 172f, 164f, 30f, 5f, 1.5f)
    limb(colors.wood, 177f, 169f, 173f, FLOOR, 3f)
    limb(colors.wood, 197f, 169f, 201f, FLOOR, 3f)
    drawLibrarian(187f, 164f, phase, colors, Arm(-11f, -44f, -29f, -80f), Arm(11f, -44f, 14f, -30f))
}

/** Offering a book with the reader's own glyph on it to the empty slot being held for them. */
private fun DrawScope.drawYouScene(phase: Float, colors: SceneColors, reservedLabel: TextLayoutResult) {
    drawBookcase(colors, BOARD_UPPER, BOARD_MIDDLE, BOARD_LOWER)
    YouShelves.forEach { drawSpine(it) }
    roundRect(colors.bg1, 124f, 122f, 31f, 39.5f, 2f)
    drawRoundRect(
        color = colors.ink2,
        topLeft = Offset(124f, 122f),
        size = Size(31f, 39.5f),
        cornerRadius = CornerRadius(2f),
        style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 2.5f))),
    )
    translate(130f, 124f) {
        scale(0.8f, pivot = Offset.Zero) { drawYouGlyph(colors.accent, 1.8f) }
    }
    drawText(reservedLabel, topLeft = Offset(139.5f - reservedLabel.size.width / 2f, 150f))
    drawCart(30f, colors)
    translate(left = -7f * shelvedAmount(phase)) {
        roundRect(BookColors[3], 164f, 118f, 22f, 33f, 2f)
        translate(168f, 127f) {
            scale(0.58f, pivot = Offset.Zero) { drawYouGlyph(Collar, 2.4f) }
        }
    }
    drawLibrarian(207f, FLOOR - 3f, phase, colors, Arm(-11f, -44f, -21f, -48f), Arm(11f, -44f, 15f, -26f))
}

private fun DrawScope.drawYouGlyph(color: Color, width: Float) {
    drawPath(YouGlyph, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawBookcase(colors: SceneColors, vararg boards: Float) {
    drawRect(colors.back, Offset(20f, 18f), Size(224f, FLOOR - 18f))
    boards.forEach { drawRect(colors.wood, Offset(20f, it), Size(224f, 5f)) }
    roundRect(colors.wood, 14f, 14f, 236f, 7f, 1f)
    drawRect(colors.wood, Offset(16f, 18f), Size(7f, FLOOR - 18f))
    drawRect(colors.wood, Offset(241f, 18f), Size(7f, FLOOR - 18f))
}

private fun DrawScope.drawSpine(spine: Spine) {
    drawUpright(spine.x, spine.base, spine.width, spine.height, spine.color)
}

private fun DrawScope.drawUpright(x: Float, base: Float, width: Float, height: Float, color: Color) {
    roundRect(color, x, base - height, width, height, 1.2f)
    drawRect(SpineBand, Offset(x, base - height + 5f), Size(width, 2f))
}

private fun DrawScope.drawLyingBook(x: Float, base: Float, color: Color, width: Float = 34f) {
    roundRect(color, x, base - 9f, width, 8.4f, 1.2f)
    drawRect(SpineBand, Offset(x + width - 6f, base - 9f), Size(1.8f, 8.4f))
}

/** A slightly uneven stack, which is what makes it look hand-placed. */
private fun DrawScope.drawBookStack(x: Float, base: Float, count: Int, seed: Int) {
    repeat(count) { i ->
        drawLyingBook(x + (i * 7 + seed) % 5 - 2f, base - i * 9f, BookColors[(i + seed) % BookColors.size])
    }
}

private fun DrawScope.drawCart(x: Float, colors: SceneColors) {
    translate(left = x) {
        drawUpright(5f, 150f, 8f, 24f, BookColors[3])
        drawUpright(14f, 150f, 9f, 20f, BookColors[1])
        rotate(14f, pivot = Offset(28f, 150f)) {
            roundRect(BookColors[2], 24f, 127f, 9f, 23f, 1.2f)
        }
        drawLyingBook(6f, 170f, BookColors[4], width = 30f)
        drawLyingBook(9f, 161f, BookColors[0], width = 26f)
        limb(colors.ink1, 0f, 150f, 54f, 150f, 2.2f)
        limb(colors.ink1, 0f, 170f, 54f, 170f, 2.2f)
        limb(colors.ink1, 2f, 150f, 2f, 178f, 2.2f)
        limb(colors.ink1, 52f, 150f, 52f, 178f, 2.2f)
        limb(colors.ink1, 52f, 150f, 60f, 140f, 2.2f)
        drawCircle(colors.ink3, 4f, Offset(7f, 181f))
        drawCircle(colors.ink3, 4f, Offset(47f, 181f))
    }
}

/** A library ladder hanging from a rail along the top of the bookcase. */
private fun DrawScope.drawLadder(colors: SceneColors) {
    limb(colors.ink2, 16f, 11f, 248f, 11f, 2f)
    limb(colors.ink1, 176f, 12f, 158f, FLOOR, 2.6f)
    limb(colors.ink1, 196f, 12f, 178f, FLOOR, 2.6f)
    var y = 30f
    while (y <= 170f) {
        val inset = 18f * (y - 14f) / 172f
        limb(colors.ink1, 176f - inset, y, 196f - inset, y, 2f)
        y += 20f
    }
    drawCircle(colors.ink2, 4f, Offset(186f, 11f))
}

/** An arm from the shoulder to the hand, relative to the librarian's feet. */
private class Arm(val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

/** The librarian, standing at ([x], [y]). */
private fun DrawScope.drawLibrarian(x: Float, y: Float, phase: Float, colors: SceneColors, vararg arms: Arm) {
    translate(x, y) {
        limb(Trousers, -5f, -2f, -5f, -24f, 6f)
        limb(Trousers, 5f, -2f, 5f, -24f, 6f)
        limb(Detail, -8f, 0f, -3f, 0f, 3.5f)
        limb(Detail, 3f, 0f, 8f, 0f, 3.5f)

        roundRect(colors.accent, -12f, -50f, 24f, 29f, 8f)
        drawPath(CollarPath, Collar)
        drawCircle(Detail, 1.1f, Offset(0f, -36f))
        drawCircle(Detail, 1.1f, Offset(0f, -29f))

        drawCircle(Hair, 5.2f, Offset(0f, -74f))
        drawCircle(Skin, 11f, Offset(0f, -61f))
        drawPath(HairPath, Hair)

        // A blink, for a moment near the end of each loop.
        if (phase !in 0.94f..0.97f) {
            drawCircle(Detail, 1.1f, Offset(-4f, -59f))
            drawCircle(Detail, 1.1f, Offset(4f, -59f))
        }
        drawCircle(Detail, 3.5f, Offset(-4f, -59f), style = Stroke(1f))
        drawCircle(Detail, 3.5f, Offset(4f, -59f), style = Stroke(1f))
        drawLine(Detail, Offset(-0.5f, -59f), Offset(0.5f, -59f), strokeWidth = 1f)
        drawPath(SmilePath, Detail, style = Stroke(1.1f, cap = StrokeCap.Round))

        arms.forEach { arm ->
            limb(colors.accent, arm.fromX, arm.fromY, arm.toX, arm.toY, 5.5f)
            drawCircle(Skin, 2.8f, Offset(arm.toX, arm.toY))
        }
    }
}

private val CollarPath = PathParser().parsePathString("M-5 -50L0 -42L5 -50z").toPath()
private val HairPath = PathParser().parsePathString("M-11.3 -60a11.3 11.3 0 0 1 22.6 0q-6-6.5-11.3-5.5q-5.3-1-11.3 5.5z").toPath()
private val SmilePath = PathParser().parsePathString("M-3 -53.5q3 2.5 6 0").toPath()

private fun DrawScope.limb(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, width: Float) {
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = width, cap = StrokeCap.Round)
}

private fun DrawScope.roundRect(color: Color, x: Float, y: Float, width: Float, height: Float, radius: Float) {
    drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(radius))
}
