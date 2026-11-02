package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags on the sync offer banner's nodes. */
internal object ReaderSyncOfferTags {
    const val BANNER = "reader_sync_offer_banner"
    const val DETAIL = "reader_sync_offer_detail"
    const val GO = "reader_sync_offer_go"
    const val DISMISS = "reader_sync_offer_dismiss"
}

/**
 * The offer from another device, over the page. It reads on every reader plate, and it takes only
 * the touches inside its own bounds, so the page under it keeps its gestures.
 *
 * @param colors the reader's chrome colours for the theme.
 * @param actionColor the "Go" colour, from [readerSyncOfferActionColor].
 * @param onAccept called when "Go" is tapped.
 * @param onDismiss called when the dismiss button is tapped.
 */
@Composable
internal fun ReaderSyncOfferBanner(
    title: String,
    detail: String,
    goLabel: String,
    dismissLabel: String,
    colors: ReaderChromeColors,
    actionColor: Color,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.md)
    Row(
        modifier
            .testTag(ReaderSyncOfferTags.BANNER)
            .clip(shape)
            .background(colors.plate)
            .border(0.5.dp, colors.border, shape)
            // Consumes every touch that starts on the banner, so none reaches the page beneath. Children
            // (Go, Dismiss) still see their own taps first, as they are dispatched before this node.
            .pointerInput(Unit) { consumeTouches() }
            .padding(start = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = uiTextStyle(13.sp, FontWeight.Medium, colors.ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = detail,
                style = uiTextStyle(11.5.sp, FontWeight.Normal, colors.labelInk),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(ReaderSyncOfferTags.DETAIL),
            )
        }
        Box(
            Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .clickable(role = Role.Button, onClick = onAccept)
                .testTag(ReaderSyncOfferTags.GO),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = goLabel, style = uiTextStyle(13.sp, FontWeight.SemiBold, actionColor))
        }
        Box(
            Modifier
                .size(48.dp)
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = dismissLabel }
                .testTag(ReaderSyncOfferTags.DISMISS),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reader_close),
                contentDescription = null,
                tint = colors.labelInk,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun ReaderSyncOfferBannerPreview() {
    val theme = ReaderTheme.Light
    OmnibusTheme(darkTheme = !theme.isLight) {
        ReaderSyncOfferBanner(
            title = stringResource(R.string.reader_sync_offer_title),
            detail = stringResource(R.string.reader_sync_offer_detail),
            goLabel = stringResource(R.string.reader_sync_offer_go),
            dismissLabel = stringResource(R.string.reader_sync_offer_dismiss),
            colors = readerChromeColors(theme),
            actionColor = readerSyncOfferActionColor(theme),
            onAccept = {},
            onDismiss = {},
        )
    }
}

/** Takes a whole touch that starts inside the banner, so no node beneath it sees the touch. */
private suspend fun PointerInputScope.consumeTouches() {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        var pressed: Boolean
        do {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            pressed = event.changes.any { it.pressed }
        } while (pressed)
    }
}
