package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.TimeZone

/**
 * The preview as drawn: text runs as they are, and each veiled run as its filler, transparent with a blurred shadow. The
 * real spoiler text is never in the result.
 */
internal fun journalPreviewText(runs: List<PreviewRun>, veilColor: Color, blurRadiusPx: Float): AnnotatedString =
    buildAnnotatedString {
        runs.forEach { run ->
            when (run) {
                is PreviewRun.Text -> append(run.text)
                is PreviewRun.Veiled -> withStyle(
                    SpanStyle(
                        color = Color.Transparent,
                        shadow = Shadow(color = veilColor, offset = Offset.Zero, blurRadius = blurRadiusPx),
                    ),
                ) { append(run.filler) }
            }
        }
    }

/**
 * One journal entry: its byline over its preview, in a rounded card. The card is one TalkBack item that reads the byline
 * and the spoken preview, in which every spoiler is "spoiler, hidden". It has no click action.
 */
@Composable
internal fun JournalCard(card: JournalCardModel, nowMillis: Long, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val locale = LocalConfiguration.current.locales[0]
    val draftLabel = stringResource(R.string.book_detail_journal_draft)
    val spoilerHidden = stringResource(R.string.book_detail_journal_spoiler_hidden)
    val byline = remember(card.authorName, card.isOwn, card.isDraft, card.createdAtMillis, nowMillis, locale, draftLabel) {
        journalByline(card.authorName, card.isOwn, card.isDraft, card.createdAtMillis, nowMillis, locale, TimeZone.getDefault(), draftLabel)
    }
    val veilColor = palette.ink0.copy(alpha = 0.55f)
    val blurPx = with(LocalDensity.current) { 6.dp.toPx() }
    val previewText = remember(card.preview, veilColor, blurPx) { journalPreviewText(card.preview, veilColor, blurPx) }
    val spoken = remember(card.preview, spoilerHidden) { spokenPreview(card.preview, spoilerHidden) }

    Column(
        modifier
            .fillMaxWidth()
            .testTag(JournalTestTags.card(card.id))
            .semantics(mergeDescendants = true) {}
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg1)
            .padding(vertical = 16.dp, horizontal = 18.dp),
    ) {
        if (byline != null) {
            Text(
                byline,
                style = uiTextStyle(size = 14, weight = FontWeight.Normal, color = palette.ink1),
                modifier = Modifier.testTag(JournalTestTags.byline(card.id)),
            )
        }
        if (card.preview.isNotEmpty()) {
            val previewTag = JournalTestTags.preview(card.id)
            Box(
                Modifier
                    .padding(top = if (byline != null) 4.dp else 0.dp)
                    .clearAndSetSemantics {
                        testTag = previewTag
                        text = AnnotatedString(spoken) // the semantics property, not the drawn string
                    },
            ) {
                Text(
                    previewText,
                    style = uiTextStyle(size = 16, weight = FontWeight.Normal, color = palette.ink0.copy(alpha = 0.9f))
                        .copy(lineHeight = 1.45.em),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
