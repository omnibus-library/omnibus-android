package com.omnibus.omnibus.feature.shelves.detail.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.data.shelves.ShelfMatchMode
import com.omnibus.omnibus.data.shelves.ShelfRules
import com.omnibus.omnibus.feature.bookdetail.more.shelves.rememberRuleWords
import com.omnibus.omnibus.feature.bookdetail.more.shelves.smartShelfRuleChip
import com.omnibus.omnibus.feature.library.ui.ShelfMosaicArt
import com.omnibus.omnibus.feature.library.ui.shelfKindGlyph
import com.omnibus.omnibus.feature.shelves.detail.ShelfDetailMetaPart
import com.omnibus.omnibus.feature.shelves.detail.shelfDetailMetaParts
import com.omnibus.omnibus.feature.shelves.ui.kindLabelRes
import com.omnibus.omnibus.feature.shelves.ui.visibilityGlyph
import com.omnibus.omnibus.feature.shelves.ui.visibilityLabelRes
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The rounded corner of the hero's mosaic. */
private val HeroMosaicCorner = 6.dp

/** The hero's identity: the mosaic at the left, the accent rule, serif name and meta line beside it, then the description and rules. */
@Composable
internal fun ShelfDetailHero(
    shelf: Shelf,
    description: String?,
    rules: ShelfRules?,
    covers: List<String>,
    onNamePositioned: (LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(
                Modifier
                    .width(104.dp)
                    .aspectRatio(2f / 3f)
                    .coverShadow(HeroMosaicCorner)
                    .clearAndSetSemantics {},
            ) {
                ShelfMosaicArt(
                    preview = ShelfPreview(shelf, covers),
                    modifier = Modifier.matchParentSize().clip(RoundedCornerShape(HeroMosaicCorner)),
                    plateLabel = stringResource(emptyLedgeRes(shelf.kind)),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Box(Modifier.size(24.dp, 1.5.dp).background(palette.accent))
                Text(
                    text = shelf.name,
                    style = displayTextStyle(30, palette.ink0),
                    modifier = Modifier
                        .semantics { heading() }
                        .testTag(ShelfDetailTestTags.HERO_NAME)
                        .onGloballyPositioned(onNamePositioned),
                )
                ShelfMetaLine(shelf)
            }
        }
        description?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = displayTextStyle(18, palette.ink2),
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        if (shelf.kind == ShelfKind.Smart && rules != null && rules.rules.isNotEmpty()) {
            ShelfRuleChips(rules)
        }
    }
}

/** The meta line: book count, kind and visibility, read as one node: "14 books, Smart, Public". */
@Composable
private fun ShelfMetaLine(shelf: Shelf) {
    val palette = OmnibusTheme.palette
    val parts = shelfDetailMetaParts(shelf)
    val texts = parts.map { metaTextOf(it) }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = texts.joinToString(", ") },
    ) {
        parts.zip(texts).forEach { (part, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val glyph = metaGlyphOf(part)
                if (glyph != null) {
                    Icon(
                        painter = painterResource(glyph),
                        contentDescription = null,
                        tint = palette.ink3,
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = text.uppercase(),
                    style = monoTextStyle(10.sp, palette.ink3).copy(letterSpacing = 0.7.sp),
                )
            }
        }
    }
}

/** The smart shelf's match mode and one chip per rule. */
@Composable
private fun ShelfRuleChips(rules: ShelfRules) {
    val palette = OmnibusTheme.palette
    val words = rememberRuleWords()
    val locale = LocalConfiguration.current.locales[0]
    Text(
        text = stringResource(
            if (rules.matchMode == ShelfMatchMode.Any) R.string.shelf_detail_matches_any else R.string.shelf_detail_matches_all,
        ).uppercase(),
        style = monoTextStyle(10.sp, palette.ink3).copy(letterSpacing = 0.8.sp),
        modifier = Modifier.padding(top = Spacing.lg),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(top = 8.dp),
    ) {
        rules.rules.forEach { rule ->
            Text(
                text = smartShelfRuleChip(rule, words, locale),
                style = uiTextStyle(14.sp, FontWeight.Medium, palette.ink0),
                modifier = Modifier
                    .background(palette.bg2, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
    }
}

/** The text of one meta part. The count uses the plural rules, the kind and visibility their Shelves labels. */
@Composable
private fun metaTextOf(part: ShelfDetailMetaPart): String = when (part) {
    is ShelfDetailMetaPart.BookCount -> {
        val count = part.count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        pluralStringResource(R.plurals.library_shelves_book_count, count, count)
    }
    is ShelfDetailMetaPart.Kind -> stringResource(kindLabelRes(part.kind))
    is ShelfDetailMetaPart.Visibility -> stringResource(visibilityLabelRes(part.visibility))
}

/** The glyph before a kind or visibility, or null for a count, which has none. */
@DrawableRes
private fun metaGlyphOf(part: ShelfDetailMetaPart): Int? = when (part) {
    is ShelfDetailMetaPart.BookCount -> null
    is ShelfDetailMetaPart.Kind -> shelfKindGlyph(part.kind)
    is ShelfDetailMetaPart.Visibility -> visibilityGlyph(part.visibility)
}

/** The empty plate's label for a shelf of [kind], as the Shelves screen words it. */
@StringRes
private fun emptyLedgeRes(kind: ShelfKind): Int = when (kind) {
    ShelfKind.Manual -> R.string.shelves_empty_ledge_manual
    ShelfKind.Smart -> R.string.shelves_empty_ledge_smart
    ShelfKind.Wishlist -> R.string.shelves_empty_ledge_wishlist
}
