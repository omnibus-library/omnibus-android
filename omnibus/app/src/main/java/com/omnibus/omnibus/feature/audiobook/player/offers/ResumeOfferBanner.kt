package com.omnibus.omnibus.feature.audiobook.player.offers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * A resume offer in the player's banner slot (P-SYNC-6, P-SYNC-7), styled like the reader's sync offer. The title and
 * detail are one polite live region, so TalkBack reads them once when the banner appears (P-A11Y-1). Go and Dismiss
 * are 48 dp buttons, and Dismiss is labelled "Dismiss" (P-A11Y-4).
 *
 * @param title The offer's title, one line.
 * @param detail The offer's detail, at most two lines.
 * @param onGo Called when the listener accepts the offer.
 * @param onDismiss Called when the listener dismisses the offer.
 */
@Composable
internal fun ResumeOfferBanner(
    title: String,
    detail: String,
    onGo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.md)
    val dismissLabel = stringResource(R.string.audiobook_dismiss)
    Row(
        modifier
            .fillMaxWidth()
            .testTag(ResumeOfferTags.BANNER)
            .clip(shape)
            .background(palette.bg1)
            .border(0.5.dp, palette.ink0.copy(alpha = 0.12f), shape)
            .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(
            Modifier
                .weight(1f)
                .testTag(ResumeOfferTags.TEXT)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            Text(
                text = title,
                modifier = Modifier.testTag(ResumeOfferTags.TITLE),
                style = uiTextStyle(13.sp, FontWeight.Medium, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                modifier = Modifier.testTag(ResumeOfferTags.DETAIL),
                style = uiTextStyle(11.5.sp, FontWeight.Normal, palette.ink0.copy(alpha = 0.5f)),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .testTag(ResumeOfferTags.GO)
                .clickable(role = Role.Button, onClick = onGo),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.audiobook_offers_go),
                style = uiTextStyle(13.sp, FontWeight.SemiBold, palette.accent),
            )
        }
        Box(
            Modifier
                .size(48.dp)
                .testTag(ResumeOfferTags.DISMISS)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = dismissLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reader_close),
                contentDescription = null,
                tint = palette.ink0.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun DeviceOfferPreview() {
    OmnibusTheme {
        ResumeOfferBanner(
            title = stringResource(R.string.audiobook_offers_device_title),
            detail = stringResource(R.string.audiobook_offers_device_detail, "1:42:10"),
            onGo = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun EbookAheadOfferPreview() {
    OmnibusTheme {
        ResumeOfferBanner(
            title = stringResource(R.string.audiobook_offers_ebook_ahead_title),
            detail = stringResource(R.string.audiobook_offers_ebook_detail, "6:38"),
            onGo = {},
            onDismiss = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun EbookBehindOfferPreview() {
    OmnibusTheme {
        ResumeOfferBanner(
            title = stringResource(R.string.audiobook_offers_ebook_behind_title),
            detail = stringResource(R.string.audiobook_offers_ebook_detail, "6:38"),
            onGo = {},
            onDismiss = {},
        )
    }
}
