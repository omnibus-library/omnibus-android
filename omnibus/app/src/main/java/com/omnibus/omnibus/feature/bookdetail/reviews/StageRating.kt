package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.reviews.mine.rememberReviewSavePulse
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import java.util.Locale

/** The alpha of the stage rating's content, so a test can read how dimmed it is. */
internal val StageRatingAlphaKey = SemanticsPropertyKey<Float>("StageRatingAlpha")
private var SemanticsPropertyReceiver.stageRatingAlpha by StageRatingAlphaKey

/** The scale of the stage rating's stars while a save pulse plays, so a test can read the pulse. */
internal val StageRatingPulseKey = SemanticsPropertyKey<Float>("StageRatingPulse")
private var SemanticsPropertyReceiver.stageRatingPulse by StageRatingPulseKey

/** The opacity of the stars and chevron while the reviews are not known yet. */
private const val DIMMED_ALPHA = 0.45f

/**
 * The household's average on the stage's status row: five stars and a chevron that opens the Reviews tab. It renders
 * nothing for a book with nothing to read, before it creates its ViewModel, so such a book needs no Hilt.
 */
internal object StageRatingSection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsReviews(context.book)) return
        val viewModel = householdReviewsViewModel(context.book)
        val pulse = rememberReviewSavePulse(context.book.uuid)
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val state by viewModel.uiState.collectAsState()
        StageRating(
            state.stage,
            context.accent.tint,
            onClick = { openReviews(context.tabs) },
            modifier = modifier,
            pulse = pulse,
        )
    }
}

/**
 * The book's one [HouseholdReviewsViewModel], which the stage rating and the Reviews tab both get. They share the key,
 * so both sections see the same instance in the same nav entry.
 */
@Composable
internal fun householdReviewsViewModel(book: Book): HouseholdReviewsViewModel =
    hiltViewModel<HouseholdReviewsViewModel, HouseholdReviewsViewModel.Factory>(
        key = "household-reviews-${book.uuid}",
        creationCallback = { it.create(book.uuid) },
    )

/**
 * The stage's rating button. It is one accessibility node that reads the state and says it opens Reviews. Until the
 * reviews are known it is dimmed.
 *
 * A change of [pulse] plays the save pulse once: the stars scale up to 1.2 and back. The first composition, a return to
 * the page and [reducedMotion] never pulse.
 *
 * @param pulse The count of saved ratings for the book, from [rememberReviewSavePulse].
 * @param reducedMotion Whether the system asks for no animation, so the pulse is skipped.
 */
@Composable
internal fun StageRating(
    model: StageRatingModel,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pulse: Int = 0,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val scale = remember { Animatable(1f) }
    // The count seen so far. Starting at the current count means only a change after composition pulses.
    var seen by remember { mutableIntStateOf(pulse) }
    LaunchedEffect(pulse) {
        if (pulse == seen) return@LaunchedEffect
        seen = pulse
        if (reducedMotion) return@LaunchedEffect
        scale.animateTo(1.2f, tween(durationMillis = 180, delayMillis = 240))
        scale.animateTo(1f, tween(durationMillis = 280))
    }
    val pulseScale = scale.value
    val phrase = when (model) {
        is StageRatingModel.Average -> pluralStringResource(
            R.plurals.book_detail_reviews_stage_average,
            model.count,
            starNumber(model.average, locale),
            model.count,
        )
        StageRatingModel.NoReviews -> stringResource(R.string.book_detail_reviews_stage_none)
        StageRatingModel.Checking -> stringResource(R.string.book_detail_reviews_stage_checking)
        StageRatingModel.NeedsConnection -> stringResource(R.string.book_detail_reviews_stage_offline)
        StageRatingModel.CouldNotLoad -> stringResource(R.string.book_detail_reviews_stage_failed)
    }
    val description = stringResource(R.string.book_detail_reviews_stage_opens, phrase)
    val alpha = if (model.dimmed) DIMMED_ALPHA else 1f

    Row(
        modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = description
                role = Role.Button
                this.onClick(label = null) {
                    onClick()
                    true
                }
                testTag = ReviewsTestTags.STAGE_RATING
                stageRatingAlpha = alpha
                stageRatingPulse = pulseScale
            }
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .alpha(alpha),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StarRow(model.stars, 18.dp, tint)
            Icon(
                imageVector = ChevronEnd,
                contentDescription = null,
                tint = palette.ink2,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

/** A chevron pointing right, which flips in right-to-left layouts. */
private val ChevronEnd: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewsChevron",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).path(
        fill = null,
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2.2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(9f, 6f)
        lineTo(15f, 12f)
        lineTo(9f, 18f)
    }.build()
}
