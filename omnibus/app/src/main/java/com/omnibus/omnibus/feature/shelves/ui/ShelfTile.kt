package com.omnibus.omnibus.feature.shelves.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.library.ui.shelfKindGlyph
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.ReaderAvatar
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for [ShelfTile]. */
internal object ShelfTileTestTags {
    fun tile(id: Long) = "shelf-tile-$id"
}

/** The corner radius of a tile, its ground and its outline. */
private val TileShape = RoundedCornerShape(16.dp)

/** The corners of a book standing on the ledge: square on the spine's side, a little rounder on the other. */
private val BookShape = RoundedCornerShape(topStart = 2.dp, bottomStart = 2.dp, topEnd = 4.dp, bottomEnd = 4.dp)

/** The ledge's width over its height. */
private const val LEDGE_ASPECT = 1.6f

/** The most books that stand on a ledge. */
private const val LEDGE_CAPACITY = 4

/**
 * One shelf on the Shelves screen as a tile: its books standing on a lit ledge, then its name, its kind and visibility,
 * and a foot with the book count and [ownerLabel] beside the owner's avatar. The tile is one node for TalkBack and has
 * no click action yet. Its height follows its width, since every line is one line, so a long name never changes it.
 *
 * @param ownerLabel What the foot calls the owner: "You", or their name.
 */
@Composable
internal fun ShelfTile(preview: ShelfPreview, ownerLabel: String, modifier: Modifier = Modifier) {
    val shelf = preview.shelf
    val palette = OmnibusTheme.palette
    val tint = remember(shelf.accent, palette.accent) {
        shelf.accent?.let(Oklch::parse)?.toColor() ?: palette.accent
    }
    val bookCount = bookCountText(shelf.bookCount)
    val description = listOf(
        shelf.name,
        bookCount,
        stringResource(kindLabelRes(shelf.kind)),
        stringResource(visibilityLabelRes(shelf.visibility)),
        ownerLabel,
    ).joinToString(", ")
    Column(
        modifier
            .clip(TileShape)
            .background(palette.bg1)
            .border(1.dp, palette.line2, TileShape)
            .clearAndSetSemantics {
                this[SemanticsProperties.TestTag] = ShelfTileTestTags.tile(shelf.id)
                contentDescription = description
            },
    ) {
        ShelfLedge(preview, tint)
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = shelf.name,
                style = displayTextStyle(19, palette.ink0, FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ShelfFacets(shelf.kind, shelf.visibility, tint)
            ShelfFoot(preview, bookCount, ownerLabel, Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * The tile's top: up to four covers standing on a plank, lit from below in [tint]; the last of several leans on the
 * rest. A shelf with nothing on it says so on the ledge. Covers are drawn from their uuids, or as blank plates when the
 * shelf has books but no cover uuids yet.
 */
@Composable
private fun ShelfLedge(preview: ShelfPreview, tint: Color) {
    val palette = OmnibusTheme.palette
    val covers = preview.coverUuids.take(LEDGE_CAPACITY)
    val blanks = if (covers.isEmpty()) preview.shelf.bookCount.coerceIn(0L, LEDGE_CAPACITY.toLong()).toInt() else 0
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(LEDGE_ASPECT)
            .drawBehind {
                drawRect(palette.bg2)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(tint.copy(alpha = 0.30f), tint.copy(alpha = 0f)),
                        center = Offset(size.width / 2f, size.height * 1.15f),
                        radius = size.width * 0.72f,
                    ),
                )
                val hairline = 1.dp.toPx()
                drawRect(palette.line2, topLeft = Offset(0f, size.height - hairline), size = Size(size.width, hairline))
            },
    ) {
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                .fillMaxWidth()
                .height(4.dp)
                .shadow(4.dp, RoundedCornerShape(2.dp))
                .background(lerp(tint, palette.bg2, 0.6f), RoundedCornerShape(2.dp)),
        )
        if (covers.isEmpty() && blanks == 0) {
            EmptyLedgeMark(preview.shelf.kind, tint)
        } else {
            Row(
                Modifier
                    .matchParentSize()
                    .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Bottom,
            ) {
                covers.forEachIndexed { index, uuid ->
                    StandingBook(leans = index == covers.lastIndex && covers.size > 1) {
                        BookCover(
                            identity = CoverIdentity(uuid = uuid, title = "", hasCover = true),
                            modifier = Modifier.fillMaxSize(),
                            size = CoverSize.Sm,
                            cornerRadius = 0.dp,
                            keepsAspect = false,
                        )
                    }
                }
                repeat(blanks) { index ->
                    StandingBook(leans = index == blanks - 1 && blanks > 1) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(lerp(tint, palette.bg2, 0.82f), palette.bg1))),
                        )
                    }
                }
            }
        }
    }
}

/** One book upright on the plank: a 2:3 face no wider than 58 dp and no taller than the ledge allows. */
@Composable
private fun RowScope.StandingBook(leans: Boolean, face: @Composable () -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier
                .widthIn(max = 58.dp)
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    if (leans) {
                        rotationZ = -6f
                        transformOrigin = TransformOrigin(1f, 1f)
                    }
                }
                .coverShadow(4.dp)
                .clip(BookShape)
                .border(0.5.dp, Color.Black.copy(alpha = 0.18f), BookShape),
        ) {
            face()
        }
    }
}

/** The ledge of an empty shelf: the kind's glyph and the empty copy inside a dashed plate. */
@Composable
private fun BoxScope.EmptyLedgeMark(kind: ShelfKind, tint: Color) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier
            .align(Alignment.Center)
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
            .drawBehind {
                drawRoundRect(
                    color = palette.line,
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            painter = painterResource(shelfKindGlyph(kind)),
            contentDescription = null,
            tint = lerp(tint, palette.ink2, 0.3f),
            modifier = Modifier.size(15.dp),
        )
        BasicText(
            text = stringResource(emptyLedgeRes(kind)).uppercase(),
            style = monoTextStyle(9.5.sp, palette.ink3).copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = 7.6.sp, maxFontSize = 9.5.sp, stepSize = 0.1.sp),
        )
    }
}

/** The kind and visibility of a shelf, in a row that collapses to the visibility's glyph when both words don't fit. */
@Composable
private fun ShelfFacets(kind: ShelfKind, visibility: ShelfVisibility, tint: Color) {
    val palette = OmnibusTheme.palette
    val kindColor = lerp(tint, palette.ink1, 0.22f)
    val kindLabel = stringResource(kindLabelRes(kind))
    val visibilityLabel = stringResource(visibilityLabelRes(visibility))
    val visibilityIcon = visibilityGlyph(visibility)
    FitsOrCollapses(
        full = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Facet(shelfKindGlyph(kind), kindLabel, kindColor)
                Facet(visibilityIcon, visibilityLabel, palette.ink2)
            }
        },
        collapsed = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Facet(shelfKindGlyph(kind), kindLabel, kindColor)
                Icon(
                    painter = painterResource(visibilityIcon),
                    contentDescription = null,
                    tint = palette.ink2,
                    modifier = Modifier.size(9.dp),
                )
            }
        },
    )
}

/** A glyph and its label in the facet style, the label upper-cased on one line. */
@Composable
private fun Facet(@DrawableRes glyph: Int, label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(glyph),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(9.dp),
        )
        Text(
            text = label.uppercase(),
            style = monoTextStyle(9.5.sp, color).copy(letterSpacing = 0.9.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Shows [full] when it fits the width it is given, else [collapsed]. It measures rather than switching on a width, so
 * the facets collapse wherever the words don't fit, at any text size. Each lambda emits exactly one layout.
 */
@Composable
private fun FitsOrCollapses(
    full: @Composable () -> Unit,
    collapsed: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(full, collapsed), modifier = modifier) { (fullItems, collapsedItems), constraints ->
        val fullPlaceable = fullItems.single().measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
        val chosen = if (fullPlaceable.width <= constraints.maxWidth) {
            fullPlaceable
        } else {
            collapsedItems.single().measure(constraints.copy(minWidth = 0))
        }
        layout(chosen.width.coerceAtMost(constraints.maxWidth), chosen.height) {
            chosen.place(0, 0)
        }
    }
}

/** The foot: the book count on the left, and the owner's avatar and [ownerLabel] on the right, ellipsised if long. */
@Composable
private fun ShelfFoot(preview: ShelfPreview, bookCount: String, ownerLabel: String, modifier: Modifier = Modifier) {
    val shelf = preview.shelf
    val palette = OmnibusTheme.palette
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bookCount,
            style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink1),
            maxLines = 1,
            softWrap = false,
        )
        Row(
            Modifier.weight(1f, fill = false).padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ReaderAvatar(
                userId = shelf.ownerUserId,
                name = shelf.ownerUsername,
                hasAvatar = shelf.ownerHasAvatar,
                size = 18.dp,
            )
            Text(
                text = ownerLabel,
                style = uiTextStyle(12, FontWeight.Normal, palette.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "1 book" or "N books" for [count]. */
@Composable
private fun bookCountText(count: Long): String {
    val n = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    return pluralStringResource(R.plurals.library_shelves_book_count, n, n)
}

@StringRes
private fun kindLabelRes(kind: ShelfKind): Int = when (kind) {
    ShelfKind.Manual -> R.string.shelves_kind_manual
    ShelfKind.Smart -> R.string.shelves_kind_smart
    ShelfKind.Wishlist -> R.string.shelves_kind_wishlist
}

@StringRes
private fun visibilityLabelRes(visibility: ShelfVisibility): Int = when (visibility) {
    ShelfVisibility.Private -> R.string.shelves_visibility_private
    ShelfVisibility.Public -> R.string.shelves_visibility_public
}

@StringRes
private fun emptyLedgeRes(kind: ShelfKind): Int = when (kind) {
    ShelfKind.Manual -> R.string.shelves_empty_ledge_manual
    ShelfKind.Smart -> R.string.shelves_empty_ledge_smart
    ShelfKind.Wishlist -> R.string.shelves_empty_ledge_wishlist
}

@DrawableRes
private fun visibilityGlyph(visibility: ShelfVisibility): Int = when (visibility) {
    ShelfVisibility.Private -> R.drawable.ic_shelf_private
    ShelfVisibility.Public -> R.drawable.ic_shelf_public
}

@PreviewLightDark
@Composable
private fun ShelfTilePreview() {
    OmnibusTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ShelfTile(
                    preview = previewTile(1L, "Compiler corner", ShelfKind.Manual, ShelfVisibility.Private, 3L, covers = 3),
                    ownerLabel = "You",
                    modifier = Modifier.weight(1f),
                )
                ShelfTile(
                    preview = previewTile(2L, "Empty smart", ShelfKind.Smart, ShelfVisibility.Public, 0L, covers = 0),
                    ownerLabel = "maya",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A shelf for the preview, owned by the viewer, with [covers] cover uuids that load nothing. */
private fun previewTile(
    id: Long,
    name: String,
    kind: ShelfKind,
    visibility: ShelfVisibility,
    bookCount: Long,
    covers: Int,
) = ShelfPreview(
    shelf = Shelf(
        id = id,
        ownerUserId = 7L,
        ownerUsername = "maya",
        kind = kind,
        name = name,
        visibility = visibility,
        accent = null,
        bookCount = bookCount,
    ),
    coverUuids = List(covers) { "preview-$id-$it" },
)
