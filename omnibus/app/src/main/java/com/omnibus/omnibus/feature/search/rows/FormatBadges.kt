package com.omnibus.omnibus.feature.search.rows

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.monoTextStyle
import java.util.Locale

/** A way a book can be read, shown as a badge on its row, in display order. */
enum class FormatBadge(@param:StringRes val label: Int) {
    Ebook(R.string.search_badge_ebook),
    Audio(R.string.search_badge_audio),
    Print(R.string.search_badge_print),
}

/**
 * The badges for a book with [formats] and [hasPhysical], in [FormatBadge] order, each at most once. Formats are compared
 * case-insensitively against the ebook and audio sets [Book] defines.
 */
fun formatBadges(formats: List<String>, hasPhysical: Boolean): List<FormatBadge> {
    val lower = formats.map { it.lowercase(Locale.ROOT) }
    return buildList {
        if (lower.any { it in Book.EBOOK_FORMATS }) add(FormatBadge.Ebook)
        if (lower.any { it in Book.AUDIO_FORMATS }) add(FormatBadge.Audio)
        if (hasPhysical) add(FormatBadge.Print)
    }
}

/** The [badges] as outlined mono chips that wrap onto a second line at large font scales. Draws nothing when empty. */
@Composable
internal fun FormatBadges(badges: List<FormatBadge>, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    val palette = OmnibusTheme.palette
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge ->
            Text(
                text = stringResource(badge.label).uppercase(Locale.ROOT),
                style = monoTextStyle(10, palette.ink2).copy(letterSpacing = 1.sp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .border(0.5.dp, palette.ink2.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
