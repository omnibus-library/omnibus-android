package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import com.omnibus.omnibus.ui.components.BookCover

/**
 * The book an outcome is about: the cover on the left, then the badge, the title, the author and any fact lines. The
 * title is the stage's announced title.
 */
@Composable
internal fun ScanBookCard(
    cover: @Composable () -> Unit,
    badge: String,
    badgeColor: Color,
    title: String,
    author: String?,
    details: List<String>,
    coverWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier.testTag(ScanTestTags.CARD),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.width(coverWidth)) { cover() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = badge, style = monoTextStyle(11, badgeColor))
            Text(
                text = title,
                style = displayTextStyle(24, palette.ink0),
                modifier = Modifier
                    .testTag(ScanTestTags.TITLE)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            author?.let { Text(text = it, style = uiTextStyle(14, FontWeight.Normal, palette.ink2)) }
            details.forEach { Text(text = it, style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2)) }
        }
    }
}

/** A library book's cover, identified by its uuid, title and first creator. */
@Composable
internal fun ScanLibraryCover(uuid: String, title: String, authors: List<String>, hasCover: Boolean, modifier: Modifier = Modifier) {
    BookCover(
        identity = CoverIdentity(uuid = uuid, title = title, creator = authors.firstOrNull(), hasCover = hasCover),
        modifier = modifier,
        cornerRadius = 4.dp,
    )
}
