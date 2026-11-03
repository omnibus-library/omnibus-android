package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** Test tags for the action bar, so UI tests can find its parts. */
internal object ActionBarTestTags {
    const val BUTTON = "book-action-bar-button"
    const val ICON = "book-action-bar-icon"
    const val LABEL = "book-action-bar-label"
    const val NO_COPY = "book-action-bar-no-copy"
    const val FIND_COPY = "book-action-bar-find-copy"
    const val CHECK_IN = "book-action-bar-check-in"
}

/** The Check in pill's wash and 1 dp rim. */
@Immutable
internal data class CheckInPillColors(val wash: Color, val rim: Color)

/** The fill the button draws, read by tests. A custom key, so accessibility services never see it. */
internal val ActionBarFillKey = SemanticsPropertyKey<Float>("ActionBarFill")

/** The book icon's horizontal scale, read by tests. A custom key, so accessibility services never see it. */
internal val ActionBarIconScaleKey = SemanticsPropertyKey<Float>("ActionBarIconScale")

/** The label's text. */
@Composable
internal fun ActionLabel.text(): String = when (this) {
    is ActionLabel.StartReading -> stringResource(R.string.book_detail_actionbar_start_reading)
    is ActionLabel.ContinueReading -> chapter?.let {
        stringResource(R.string.book_detail_actionbar_continue_chapter, it)
    } ?: stringResource(R.string.book_detail_actionbar_continue_reading)
    is ActionLabel.ReadAgain -> stringResource(R.string.book_detail_actionbar_read_again)
    is ActionLabel.StartListening -> stringResource(R.string.book_detail_actionbar_start_listening)
    is ActionLabel.ContinueListening -> stringResource(R.string.book_detail_actionbar_continue_listening)
    is ActionLabel.ListenAgain -> stringResource(R.string.book_detail_actionbar_listen_again)
}

/**
 * The action bar: a pill for [ActionBarModel.Primary], an untappable note for [ActionBarModel.NoCopy], or the two
 * wishlist pills for [ActionBarModel.Wishlist]. It is stateless, so the page's section supplies the model. [extras] is
 * #99's round buttons, shown beside the main button and not for [ActionBarModel.NoCopy] or [ActionBarModel.Wishlist].
 * The bar adds no insets or outer padding: the action area owns those.
 *
 * @param reducedMotion When true, the fill snaps and the book icon does not flip.
 * @param checkInColors The Check in pill's wash and rim. Null uses the palette's background and line colours.
 */
@Composable
internal fun BookActionBar(
    model: ActionBarModel,
    colors: ActionBarColors,
    onNavigate: (BookDetailDestination) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
    extras: @Composable RowScope.() -> Unit = {},
    checkInColors: CheckInPillColors? = null,
) {
    when (model) {
        is ActionBarModel.NoCopy -> NoCopyCapsule(model, modifier.fillMaxWidth())
        is ActionBarModel.Primary -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MainButton(model, colors, onNavigate, reducedMotion, Modifier.weight(1f))
            extras()
        }
        is ActionBarModel.Wishlist -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FindCopyButton(model, colors, onNavigate, Modifier.weight(1f))
            CheckInPill(
                model,
                checkInColors ?: CheckInPillColors(OmnibusTheme.palette.bg2, OmnibusTheme.palette.line2),
                onNavigate,
            )
        }
    }
}

/** "Find a copy": the book's colour, the label and no icon, opening the store search. */
@Composable
private fun FindCopyButton(
    model: ActionBarModel.Wishlist,
    colors: ActionBarColors,
    onNavigate: (BookDetailDestination) -> Unit,
    modifier: Modifier,
) {
    val label = stringResource(R.string.book_detail_wishlist_find_a_copy)
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(colors.base)
            .clickable(role = Role.Button) { onNavigate(model.findCopy) }
            .semantics { contentDescription = label }
            .testTag(ActionBarTestTags.FIND_COPY)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = uiTextStyle(17, FontWeight.SemiBold, colors.content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Check in": a washed pill with a 1 dp rim, opening the check-in page. It wraps its label and never ellipsizes it. */
@Composable
private fun CheckInPill(
    model: ActionBarModel.Wishlist,
    pill: CheckInPillColors,
    onNavigate: (BookDetailDestination) -> Unit,
) {
    val label = stringResource(R.string.book_detail_wishlist_check_in)
    Box(
        Modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(pill.wash)
            .border(1.dp, pill.rim, CircleShape)
            .clickable(role = Role.Button) { onNavigate(model.checkIn) }
            .semantics { contentDescription = label }
            .testTag(ActionBarTestTags.CHECK_IN)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = uiTextStyle(16, FontWeight.SemiBold, OmnibusTheme.palette.ink0),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The main button: the book's colour, a fill to the reader's place, the label and an icon. */
@Composable
private fun MainButton(
    primary: ActionBarModel.Primary,
    colors: ActionBarColors,
    onNavigate: (BookDetailDestination) -> Unit,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val text = primary.label.text()
    val fillState = animateFloatAsState(
        targetValue = primary.fill,
        animationSpec = if (reducedMotion) snap<Float>() else Motion.settle<Float>(),
        label = "action-bar-fill",
    )
    val fill = fillState.value
    val flip = remember { Animatable(1f) }
    val iconScale = flip.value
    val scope = rememberCoroutineScope()
    val flips = !primary.label.isListening && !reducedMotion
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .drawBehind {
                drawRect(colors.base)
                fillStops(fill, size.width, ActionBarSoftEdge.toPx())?.let { stops ->
                    val rtl = layoutDirection == LayoutDirection.Rtl
                    drawRect(
                        Brush.horizontalGradient(
                            0f to colors.fill,
                            stops.solidEnd to colors.fill,
                            stops.fadeEnd to colors.fill.copy(alpha = 0f),
                            startX = if (rtl) size.width else 0f,
                            endX = if (rtl) 0f else size.width,
                        ),
                    )
                }
            }
            .clickable(role = Role.Button) {
                onNavigate(primary.destination)
                if (flips) scope.launch {
                    flip.snapTo(1f)
                    flip.animateTo(-1f, tween(320))
                    flip.animateTo(1f, tween(320))
                }
            }
            .semantics {
                contentDescription = text
                this[ActionBarFillKey] = fill
            }
            .testTag(ActionBarTestTags.BUTTON)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val iconModifier = Modifier.testTag(ActionBarTestTags.ICON)
            if (primary.label.isListening) {
                AudioBarsMark(
                    colors.content,
                    iconModifier.semantics { this[ActionBarIconScaleKey] = 1f },
                )
            } else {
                Icon(
                    OpenBookIcon,
                    contentDescription = null,
                    tint = colors.content,
                    modifier = iconModifier
                        .size(21.dp)
                        .graphicsLayer { scaleX = iconScale }
                        .semantics { this[ActionBarIconScaleKey] = iconScale },
                )
            }
            Text(
                text,
                modifier = Modifier.weight(1f, fill = false).testTag(ActionBarTestTags.LABEL),
                style = uiTextStyle(17, FontWeight.SemiBold, colors.content),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The note for a book with no readable copy: subdued, with no icon, no fill and no tap. */
@Composable
private fun NoCopyCapsule(model: ActionBarModel.NoCopy, modifier: Modifier) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(CircleShape)
            .background(OmnibusTheme.palette.bg2)
            .semantics(mergeDescendants = true) {}
            .testTag(ActionBarTestTags.NO_COPY)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                if (model.physical) R.string.book_detail_actionbar_physical_copy else R.string.book_detail_actionbar_no_copy,
            ),
            style = uiTextStyle(15, FontWeight.Medium, OmnibusTheme.palette.ink1),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
