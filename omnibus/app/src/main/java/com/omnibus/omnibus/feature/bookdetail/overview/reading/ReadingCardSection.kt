package com.omnibus.omnibus.feature.bookdetail.overview.reading

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import java.util.TimeZone

/**
 * The Your reading card on the Overview tab: when the reader started the book, their time, pace and sittings, or one
 * honest line when there is nothing to show. A wishlist-only book shows the card's wishlist line with no ViewModel. A
 * book with no copy renders nothing, before it creates its ViewModel, so such a book needs no Hilt.
 */
internal object ReadingCardSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        if (book.isWishlistOnly) {
            ReadingCard(ReadingCardModel(ReadingLine.Wishlist, emptyList()), nowMillis = 0L, modifier)
            return
        }
        if (!showsReadingCard(book)) return
        // hasFile is in the key, so a book whose files change gets a fresh ViewModel with the right empty-state line.
        val hasFile = book.hasEbook || book.hasAudiobook
        val viewModel = hiltViewModel<ReadingCardViewModel, ReadingCardViewModel.Factory>(
            key = "reading-card-${book.uuid}-$hasFile",
            creationCallback = { it.create(book.uuid, hasFile) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        ReadingCard(state.card, state.nowMillis, modifier)
    }
}

/** Whether the card shows: the book has an ebook, an audiobook or a physical copy. */
internal fun showsReadingCard(book: Book): Boolean = book.hasEbook || book.hasAudiobook || book.hasPhysical

/** The card's heading, its line and one row per [ReadingRow], on the page's raised ground. */
@Composable
internal fun ReadingCard(card: ReadingCardModel, nowMillis: Long, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val wording = remember(resources, configuration) { readingWording(resources) }
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val timeZone = remember { TimeZone.getDefault() }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .testTag(ReadingCardTestTags.CARD)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg1)
            .padding(20.dp),
    ) {
        Text(
            text = stringResource(R.string.book_detail_reading_title),
            style = displayTextStyle(26.sp, palette.ink0, FontWeight.SemiBold),
            modifier = Modifier
                .semantics { heading() }
                .testTag(ReadingCardTestTags.TITLE),
        )
        card.line?.let { line ->
            Text(
                text = line.text(wording, nowMillis, locale, timeZone),
                style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink1),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .testTag(ReadingCardTestTags.LINE),
            )
        }
        if (card.rows.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                HorizontalDivider(thickness = 0.5.dp, color = palette.line)
                card.rows.forEach { row ->
                    ReadingRowItem(row, wording, nowMillis, locale, timeZone)
                    HorizontalDivider(thickness = 0.5.dp, color = palette.line)
                }
            }
        }
    }
}

/** One row: its label at the start and its value at the end. TalkBack reads it as one item, label then value. */
@Composable
private fun ReadingRowItem(
    row: ReadingRow,
    wording: ReadingWording,
    nowMillis: Long,
    locale: Locale,
    timeZone: TimeZone,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = row.contentDescription(wording, nowMillis, locale, timeZone)
                this[SemanticsProperties.TestTag] = ReadingCardTestTags.row(row.kind)
            }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = row.label(wording),
            style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink1),
        )
        Text(
            text = row.value(wording, nowMillis, locale, timeZone),
            style = uiTextStyle(15.sp, FontWeight.Medium, palette.ink0)
                .copy(fontFeatureSettings = "lnum, tnum", textAlign = TextAlign.End),
            modifier = Modifier.weight(1f),
        )
    }
}

/** The card's words, filled from the string resources. */
private fun readingWording(resources: Resources): ReadingWording = ReadingWording(
    noReading = resources.getString(R.string.book_detail_reading_line_no_reading),
    wishlist = resources.getString(R.string.book_detail_reading_line_wishlist),
    historyFailed = resources.getString(R.string.book_detail_reading_line_history_failed),
    paperCopy = resources.getString(R.string.book_detail_reading_line_paper_copy),
    syncPending = resources.getString(R.string.book_detail_reading_line_sync_pending),
    readingSince = { date -> resources.getString(R.string.book_detail_reading_line_reading_since, date) },
    finished = { date -> resources.getString(R.string.book_detail_reading_line_finished, date) },
    labels = mapOf(
        ReadingRowKind.Started to resources.getString(R.string.book_detail_reading_row_started),
        ReadingRowKind.TimeRead to resources.getString(R.string.book_detail_reading_row_time_read),
        ReadingRowKind.Pace to resources.getString(R.string.book_detail_reading_row_pace),
        ReadingRowKind.LastRead to resources.getString(R.string.book_detail_reading_row_last_read),
        ReadingRowKind.LongestSitting to resources.getString(R.string.book_detail_reading_row_longest_sitting),
        ReadingRowKind.TimeLeft to resources.getString(R.string.book_detail_reading_row_time_left),
    ),
    timeRead = { duration, count ->
        resources.getString(
            R.string.book_detail_reading_time_read_value,
            duration,
            resources.getQuantityString(R.plurals.book_detail_reading_sessions, count, count),
        )
    },
    pace = { percent -> resources.getString(R.string.book_detail_reading_pace_value, percent) },
    longest = { duration, date -> resources.getString(R.string.book_detail_reading_longest_sitting_value, duration, date) },
    timeLeft = { duration -> resources.getString(R.string.book_detail_reading_time_left_value, duration) },
    rowDescription = { label, value -> resources.getString(R.string.book_detail_reading_row_description, label, value) },
    today = resources.getString(R.string.book_detail_reading_today),
    yesterday = resources.getString(R.string.book_detail_reading_yesterday),
    underAMinute = resources.getString(R.string.book_detail_reading_under_a_minute),
)
