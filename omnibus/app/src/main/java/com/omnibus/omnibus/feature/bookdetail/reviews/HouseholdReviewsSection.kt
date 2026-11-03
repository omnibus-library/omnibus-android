package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRow
import com.omnibus.omnibus.ui.components.ReaderAvatar
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import java.util.TimeZone

/**
 * The household's reviews on the Reviews tab: the other readers' ratings and reviews, in the server's order. It
 * renders nothing for a book with nothing to read, before it creates its ViewModel, so such a book needs no Hilt.
 */
internal object HouseholdReviewsSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsReviews(context.book)) return
        val viewModel = householdReviewsViewModel(context.book)
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        HouseholdReviews(
            model = state.tab,
            tint = context.accent.tint,
            nowMillis = state.nowMillis,
            onReveal = viewModel::reveal,
            onRetry = viewModel::retry,
            modifier = modifier,
        )
    }
}

/**
 * The Reviews tab's content for [model]: a notice while the reviews are not available, the empty message, or the
 * household's list. It renders nothing for [ReviewsTabModel.Hidden].
 */
@Composable
internal fun HouseholdReviews(
    model: ReviewsTabModel,
    tint: Color,
    nowMillis: Long,
    onReveal: (Long) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model == ReviewsTabModel.Hidden) return
    val palette = OmnibusTheme.palette
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }

    Column(modifier.fillMaxWidth().testTag(ReviewsTestTags.SECTION)) {
        when (model) {
            ReviewsTabModel.Hidden -> Unit
            ReviewsTabModel.Empty -> {
                Text(
                    text = stringResource(R.string.book_detail_reviews_empty_title),
                    style = displayTextStyle(26.sp, palette.ink0, FontWeight.SemiBold),
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.book_detail_reviews_empty_message),
                    style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink1),
                )
            }
            ReviewsTabModel.Offline -> Text(
                text = stringResource(R.string.book_detail_reviews_tab_offline),
                style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink2),
            )
            ReviewsTabModel.Failed -> {
                Text(
                    text = stringResource(R.string.book_detail_reviews_tab_failed),
                    style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink2),
                )
                TextLinkButton(stringResource(R.string.book_detail_reviews_retry), palette.ink0, 15, onRetry)
            }
            is ReviewsTabModel.Listed -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = stringResource(R.string.book_detail_reviews_household),
                        style = displayTextStyle(30.sp, palette.ink0, FontWeight.SemiBold),
                        modifier = Modifier
                            .alignByBaseline()
                            .semantics { heading() }
                            .testTag(ReviewsTestTags.HOUSEHOLD_TITLE),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = model.count.toString(),
                        style = uiTextStyle(17.sp, FontWeight.Medium, palette.ink3),
                        modifier = Modifier.alignByBaseline().testTag(ReviewsTestTags.COUNT),
                    )
                    Spacer(Modifier.weight(1f))
                    model.average?.let { average ->
                        Text(
                            text = stringResource(R.string.book_detail_reviews_average, starNumber(average, locale)),
                            style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink2),
                            modifier = Modifier.alignByBaseline().testTag(ReviewsTestTags.AVERAGE),
                        )
                    }
                }
                model.entries.forEachIndexed { index, entry ->
                    // Keyed by reader, so a review's Read more state stays with that review.
                    key(entry.userId) {
                        if (index > 0) HorizontalDivider(thickness = 1.dp, color = palette.line)
                        ReviewRow(entry, model.finished, tint, nowMillis, locale, onReveal)
                    }
                }
            }
        }
    }
}

/** One other reader's review: avatar, name and date, stars, and the body. */
@Composable
private fun ReviewRow(
    entry: ReviewEntry,
    finished: Boolean,
    tint: Color,
    nowMillis: Long,
    locale: Locale,
    onReveal: (Long) -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp)
            .testTag(ReviewsTestTags.entry(entry.userId)),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ReaderAvatar(entry.userId, entry.name, entry.hasAvatar, 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ReviewNameLine(entry, nowMillis, locale)
            entry.rating?.let { rating ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StarRow(rating, 15.dp, tint, Modifier.testTag(ReviewsTestTags.stars(entry.userId)))
                    // The number is spoken as part of the stars, so it is not read a second time.
                    Text(
                        text = starNumber(rating, locale),
                        style = uiTextStyle(14.sp, FontWeight.Normal, palette.ink1),
                        modifier = Modifier.clearAndSetSemantics {},
                    )
                }
            }
            if (entry.veiled) {
                VeiledReview(entry.userId, onReveal)
            } else {
                ReviewBodyText(entry, finished)
            }
        }
    }
}

/** The name with the date the review last changed, such as "Maren · June 14". The date is left out when unknown. */
@Composable
private fun ReviewNameLine(entry: ReviewEntry, nowMillis: Long, locale: Locale) {
    val palette = OmnibusTheme.palette
    val dateMillis = entry.dateMillis
    if (dateMillis == null) {
        Text(entry.name, style = uiTextStyle(16.sp, FontWeight.SemiBold, palette.ink0))
        return
    }
    val date = BookDetailFormat.longDate(dateMillis, nowMillis, locale, TimeZone.getDefault())
    val line = stringResource(R.string.book_detail_reviews_name_date, entry.name, date)
    val nameStart = line.indexOf(entry.name).coerceAtLeast(0)
    Text(
        text = buildAnnotatedString {
            append(line)
            addStyle(
                SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.ink0),
                nameStart,
                nameStart + entry.name.length,
            )
        },
        style = uiTextStyle(14.sp, FontWeight.Normal, palette.ink2),
    )
}

/** A spoiler the reader has not opened: the warning and a Show review button. The stars stay above it. */
@Composable
private fun VeiledReview(userId: Long, onReveal: (Long) -> Unit) {
    val palette = OmnibusTheme.palette
    Text(
        text = stringResource(R.string.book_detail_reviews_spoiler_warning),
        style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink1),
    )
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button) { onReveal(userId) }
            .testTag(ReviewsTestTags.showReview(userId)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = stringResource(R.string.book_detail_reviews_show_review),
            style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0),
        )
    }
}

/** A review's body: its text, or the note for a rating alone or an image. */
@Composable
private fun ReviewBodyText(entry: ReviewEntry, finished: Boolean) {
    val palette = OmnibusTheme.palette
    when (entry.body) {
        ReviewBody.Text -> {
            val inlineSpoiler = stringResource(R.string.book_detail_reviews_inline_spoiler)
            val text = remember(entry.markdown, finished, inlineSpoiler) {
                reviewPlainText(entry.markdown, finished, inlineSpoiler)
            }
            ExpandableReviewText(text.orEmpty(), entry.userId)
        }
        ReviewBody.ImageOnly -> Text(
            text = stringResource(R.string.book_detail_reviews_image_only),
            style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink3),
        )
        ReviewBody.RatedOnly -> Text(
            text = stringResource(R.string.book_detail_reviews_rated_only),
            style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink3),
        )
        ReviewBody.None -> Unit
    }
}

/** How tall a review's text may be before it is clipped behind Read more. */
private val COLLAPSED_HEIGHT = 116.dp

/**
 * A review's text, clipped to [COLLAPSED_HEIGHT] with a Read more button when the full text is taller. The clip is
 * decided by measuring the text at the real width, and expanding it is one way.
 */
@Composable
private fun ExpandableReviewText(text: String, userId: Long) {
    val palette = OmnibusTheme.palette
    val style = uiTextStyle(16.sp, FontWeight.Normal, palette.ink1).copy(lineHeight = 23.sp)
    var expanded by rememberSaveable { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val limitPx = with(LocalDensity.current) { (COLLAPSED_HEIGHT + 1.dp).toPx() }
        val overflows = remember(text, constraints.maxWidth, style, limitPx) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = constraints.maxWidth)).size.height > limitPx
        }
        val clip = overflows && !expanded
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (clip) Modifier.heightIn(max = COLLAPSED_HEIGHT).clipToBounds() else Modifier)
                    .testTag(ReviewsTestTags.body(userId)),
            ) {
                Text(text, style = style, modifier = Modifier.wrapContentHeight(Alignment.Top, unbounded = true))
            }
            if (clip) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { expanded = true }
                        .testTag(ReviewsTestTags.readMore(userId)),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(R.string.book_detail_reviews_read_more),
                        style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0),
                    )
                }
            }
        }
    }
}
