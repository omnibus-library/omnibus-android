package com.omnibus.omnibus.feature.bookdetail.finish

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.data.cover.CoverSize
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.coverShadow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The card for a finished book. [ground] is [finishGround]'s two stops. [riseFraction] is 0 at rest and 1 at the start
 * of the rise, read in the draw phase so the rise does not recompose.
 */
@Composable
internal fun FinishCard(
    model: FinishCardModel,
    ground: List<Color>,
    onLeaveReview: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    riseFraction: () -> Float = { 0f },
) {
    val palette = OmnibusTheme.palette
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val wording = remember(resources, configuration) { finishWording(resources) }
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .graphicsLayer {
                val fraction = riseFraction()
                translationY = RISE_OFFSET_DP.dp.toPx() * fraction
                alpha = 1f - fraction
            }
            .testTag(FinishTestTags.CARD)
            .clip(shape)
            .background(Brush.linearGradient(ground, start = Offset.Zero, end = Offset.Infinite))
            .border(1.dp, palette.ink0.copy(alpha = 0.35f), shape),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 13.dp, bottom = 12.dp)) {
            Text(
                text = wording.title(model.title),
                style = displayTextStyle(26.sp, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() }.testTag(FinishTestTags.TITLE),
            )
            model.totals.totalsText(wording)?.let { totals ->
                Text(
                    text = totals,
                    style = uiTextStyle(14.sp, FontWeight.Normal, palette.ink1),
                    modifier = Modifier.padding(top = 5.dp).testTag(FinishTestTags.TOTALS),
                )
            }
        }
        when (val action = model.action) {
            FinishAction.Review -> {
                HorizontalDivider(thickness = 1.dp, color = palette.ink0.copy(alpha = 0.16f))
                FinishRow(
                    minHeight = 60.dp,
                    title = wording.reviewTitle,
                    detail = wording.reviewDetail,
                    clickLabel = wording.reviewAction,
                    onClick = onLeaveReview,
                    tag = FinishTestTags.REVIEW_ROW,
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(palette.ink0.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = FinishStarIcon,
                            contentDescription = null,
                            tint = palette.ink0,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            is FinishAction.UpNext -> {
                HorizontalDivider(thickness = 1.dp, color = palette.ink0.copy(alpha = 0.16f))
                FinishRow(
                    minHeight = 70.dp,
                    title = wording.nextTitle(action.member.title),
                    detail = nextDetail(action.member.seriesIndex, action.seriesName, wording),
                    clickLabel = wording.nextAction,
                    onClick = { onOpenBook(action.member.uuid) },
                    tag = FinishTestTags.NEXT_ROW,
                ) {
                    BookCover(
                        identity = CoverIdentity(
                            action.member.uuid,
                            action.member.title,
                            null,
                            action.member.accent,
                            action.member.hasCover,
                        ),
                        modifier = Modifier.size(36.dp, 54.dp).rotate(-2.5f).coverShadow(3.dp),
                        size = CoverSize.Sm,
                        cornerRadius = 3.dp,
                        keepsAspect = false,
                    )
                }
            }
            null -> Unit
        }
    }
}

/**
 * One tappable row of the card: its [leading] mark, a title over a detail line and a chevron. It is one button for
 * TalkBack, reading both lines, with [clickLabel] as its click action.
 */
@Composable
private fun FinishRow(
    minHeight: Dp,
    title: String,
    detail: String,
    clickLabel: String,
    onClick: () -> Unit,
    tag: String,
    leading: @Composable () -> Unit,
) {
    val palette = OmnibusTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
            .testTag(tag)
            .padding(start = 18.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Column(Modifier.weight(1f)) {
            Text(title, style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0))
            Text(
                detail,
                style = uiTextStyle(13.sp, FontWeight.Normal, palette.ink1),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Icon(
            imageVector = FinishChevronIcon,
            contentDescription = null,
            tint = palette.ink0,
            modifier = Modifier.size(16.dp),
        )
    }
}

/**
 * [FinishCard] that rises in when [rises] is a play [latch] has not handed out yet. With [reducedMotion] the play is
 * claimed and the card stays at rest.
 */
@Composable
internal fun RisingFinishCard(
    model: FinishCardModel,
    ground: List<Color>,
    rises: Int,
    latch: PlayLatch,
    reducedMotion: Boolean,
    onLeaveReview: () -> Unit,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rise = remember { Animatable(if (!reducedMotion && latch.isPending(rises)) 1f else 0f) }
    LaunchedEffect(rises) {
        if (latch.claim(rises) && !reducedMotion) {
            rise.snapTo(1f)
            rise.animateTo(0f, tween(RISE_MILLIS, easing = FinishEasing))
        } else {
            rise.snapTo(0f)
        }
    }
    FinishCard(model, ground, onLeaveReview, onOpenBook, modifier, riseFraction = { rise.value })
}

/** The card's two gradient stops for [book] on the current theme, from its cover tone. */
@Composable
internal fun rememberFinishGround(book: Book): List<Color> {
    val light = isLightGround(OmnibusTheme.palette.bg0)
    return remember(book.accent, book.displayTitle, light) { finishGround(book.coverIdentity().tone, light) }
}
