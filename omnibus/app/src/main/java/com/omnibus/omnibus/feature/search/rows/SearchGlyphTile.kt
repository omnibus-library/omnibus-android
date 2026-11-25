package com.omnibus.omnibus.feature.search.rows

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The square tile that stands in for a book cover on a row: [glyph] tinted secondary ink on a raised ground. */
@Composable
fun SearchGlyphTile(@DrawableRes glyph: Int, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(palette.bg2),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(glyph), null, tint = palette.ink2, modifier = Modifier.size(20.dp))
    }
}

/**
 * A row whose leading tile is a [glyph] rather than a cover, with a [title] and a [subtitle] on two lines. Its spoken text
 * is [description], one focusable element that calls [onClick]. Laid out as [SearchBookRow] is.
 */
@Composable
internal fun SearchGlyphRow(
    @DrawableRes glyph: Int,
    title: AnnotatedString,
    subtitle: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchLeadingRow(
        leading = { SearchGlyphTile(glyph) },
        title = title,
        subtitle = subtitle,
        description = description,
        onClick = onClick,
        modifier = modifier,
    )
}

/** [SearchGlyphRow]'s layout with any [leading] tile, such as an author's initials. */
@Composable
internal fun SearchLeadingRow(
    leading: @Composable () -> Unit,
    title: AnnotatedString,
    subtitle: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = Spacing.screen, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = uiTextStyle(17, FontWeight.SemiBold, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painterResource(R.drawable.ic_reader_chevron_right),
            null,
            tint = palette.ink2,
            modifier = Modifier.size(20.dp),
        )
    }
}
