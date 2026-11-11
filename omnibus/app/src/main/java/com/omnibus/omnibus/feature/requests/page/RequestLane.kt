package com.omnibus.omnibus.feature.requests.page

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.requests.BookRequest
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.data.requests.RequestState
import com.omnibus.omnibus.data.requests.RequestVersion
import com.omnibus.omnibus.feature.requests.RequestButton
import com.omnibus.omnibus.feature.requests.RequestButtonWidth
import com.omnibus.omnibus.feature.requests.RequestEmphasis
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.SEP
import com.omnibus.omnibus.feature.requests.StepStatus
import com.omnibus.omnibus.feature.requests.askedBy
import com.omnibus.omnibus.feature.requests.convertsToEpub
import com.omnibus.omnibus.feature.requests.coverSizesLine
import com.omnibus.omnibus.feature.requests.isDownloading
import com.omnibus.omnibus.feature.requests.offersLookingRetry
import com.omnibus.omnibus.feature.requests.downloadPercent
import com.omnibus.omnibus.feature.requests.firstShown
import com.omnibus.omnibus.feature.requests.kindChip
import com.omnibus.omnibus.feature.requests.kindLabel
import com.omnibus.omnibus.feature.requests.lookingLabel
import com.omnibus.omnibus.feature.requests.offersCancel
import com.omnibus.omnibus.feature.requests.pickedHeading
import com.omnibus.omnibus.feature.requests.pickedMeta
import com.omnibus.omnibus.feature.requests.pickedVersion
import com.omnibus.omnibus.feature.requests.recency
import com.omnibus.omnibus.feature.requests.stateView
import com.omnibus.omnibus.feature.requests.stepsFor
import com.omnibus.omnibus.feature.requests.timeLeft
import com.omnibus.omnibus.feature.requests.versionSource
import com.omnibus.omnibus.feature.requests.versionsFound
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.ReaderAvatar
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** What a lane's controls do. Each is a user intent on the page's ViewModel. */
internal data class RequestActions(
    val onChoose: (laneId: Long, versionId: Long) -> Unit = { _, _ -> },
    val onSwap: (laneId: Long) -> Unit = {},
    val onRetry: (laneId: Long) -> Unit = {},
    val onTakeCover: (laneId: Long) -> Unit = {},
    val onKeepCover: (laneId: Long) -> Unit = {},
    val onStart: (RequestKind) -> Unit = {},
    val onCancel: (laneId: Long) -> Unit = {},
    val onUndo: () -> Unit = {},
)

/** Commands never queue offline, so every write control waits for the server to be reachable and for any write to finish. */
internal val BookRequestUIState.isLocked: Boolean get() = busy != null || !isOnline

/** A card's ground and hairline, shared by the lanes and the cards inside them. */
@Composable
internal fun LaneCard(
    modifier: Modifier = Modifier,
    radius: Dp = Radius.lg,
    fill: Color = OmnibusTheme.palette.bg2,
    accented: Boolean = false,
    spacing: Dp = Spacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(radius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(0.5.dp, if (accented) palette.accent.copy(alpha = ACCENT_BORDER_ALPHA) else palette.line2, shape)
            .padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

private const val ACCENT_BORDER_ALPHA = 0.55f

/** The format a lane is for, boxed in mono. */
@Composable
internal fun KindChip(kind: RequestKind, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Text(
        text = kindChip(kind),
        style = monoTextStyle(9.5.sp, palette.ink2),
        modifier = modifier
            .clearAndSetSemantics {}
            .border(0.5.dp, palette.line2, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.5.dp),
    )
}

/**
 * One request's card: its format, who asked, the stepper (or a stuck or cancelled request's words) and the body for
 * the state it is in.
 */
@Composable
internal fun RequestLane(
    lane: BookRequest,
    uiState: BookRequestUIState,
    wording: RequestWording,
    actions: RequestActions,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val listing = uiState.listingFor(lane)
    LaneCard(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            KindChip(lane.kind)
            Text(kindLabel(lane.kind, wording), style = uiTextStyle(16, FontWeight.Medium, palette.ink0))
            if (listing != null) {
                ReaderAvatar(lane.userId, listing.requesterName, listing.requesterHasAvatar, 20.dp)
                Text(
                    text = askedBy(listing, uiState.viewer.id, uiState.nowMillis, wording),
                    style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        val steps = stepsFor(lane, wording)
        if (steps != null) Stepper(steps) else StateNote(lane, wording)
        LaneBody(lane, uiState, wording, actions)
        LaneActionRow(lane, uiState, actions)
    }
}

@Composable
private fun Stepper(steps: List<com.omnibus.omnibus.feature.requests.RequestStep>) {
    val palette = OmnibusTheme.palette
    val current = stringResource(R.string.requests_step_current)
    val done = stringResource(R.string.requests_step_done)
    val todo = stringResource(R.string.requests_step_todo)
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        steps.forEach { step ->
            val tint = when (step.status) {
                StepStatus.Done -> palette.ok
                StepStatus.Current -> palette.accent
                StepStatus.Todo -> palette.ink3
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    stateDescription = when (step.status) {
                        StepStatus.Current -> current
                        StepStatus.Done -> done
                        StepStatus.Todo -> todo
                    }
                },
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(tint))
                Text(
                    text = step.label,
                    style = uiTextStyle(
                        14,
                        if (step.status == StepStatus.Current) FontWeight.Medium else FontWeight.Normal,
                        when (step.status) {
                            StepStatus.Todo -> palette.ink3
                            StepStatus.Current -> palette.accent
                            StepStatus.Done -> palette.ink1
                        },
                    ),
                )
            }
        }
    }
}

/** A stuck or cancelled lane says so in words: the heading, and the error or general line under it. */
@Composable
private fun StateNote(lane: BookRequest, wording: RequestWording) {
    val palette = OmnibusTheme.palette
    val view = stateView(lane.state, lane.stuckReason, lane.kind, lane.stuckDetail, wording)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = view.heading,
            style = uiTextStyle(14.5.sp, FontWeight.Medium, if (lane.state == RequestState.Stuck) palette.bad else palette.ink1),
        )
        view.note?.let { Text(it, style = uiTextStyle(13, FontWeight.Normal, palette.ink2)) }
    }
}

@Composable
private fun LaneBody(lane: BookRequest, uiState: BookRequestUIState, wording: RequestWording, actions: RequestActions) {
    val palette = OmnibusTheme.palette
    when (lane.state) {
        RequestState.Looking -> {
            LookingCard(lookingLabel(lane, uiState.nowMillis, wording))
            if (offersLookingRetry(lane, uiState.viewer, uiState.nowMillis)) {
                Text(stringResource(R.string.requests_still_looking), style = uiTextStyle(13, FontWeight.Normal, palette.ink2))
                TryAgain(lane, uiState, actions)
            }
        }
        RequestState.Choose -> VersionList(lane, uiState, wording, actions)
        RequestState.Chosen -> PickedCard(lane, meta = null)
        RequestState.OnItsWay -> DownloadCard(lane, timeLeft(lane, uiState.nowMillis, wording))
        RequestState.Ready -> {
            if (pickedVersion(lane) != null) {
                PickedCard(
                    lane,
                    meta = listOfNotNull(
                        pickedVersion(lane)?.indexer,
                        stringResource(R.string.requests_added, recency(lane.updatedAt, uiState.nowMillis, wording)),
                    ).joinToString(SEP),
                )
            }
            val offer = lane.coverOffer
            if (uiState.viewer.canChoose && offer != null) CoverOfferCard(lane, offer, uiState, wording, actions)
        }
        RequestState.Stuck -> if (uiState.viewer.canChoose) {
            TryAgain(lane, uiState, actions)
        } else {
            Text(
                text = stringResource(R.string.requests_waiting_for_chooser_retry),
                style = uiTextStyle(13, FontWeight.Normal, palette.ink2),
            )
        }
        RequestState.Cancelled -> if (uiState.offersRequestAgain(lane)) {
            StartButton(lane.kind, stringResource(R.string.requests_request_again), uiState, wording, actions)
        }
    }
}

/** The Looking card: a spinner and what the search is doing. */
@Composable
private fun LookingCard(label: String) {
    val palette = OmnibusTheme.palette
    LaneCard(radius = Radius.md, fill = palette.bg1) {
        Column(
            modifier = Modifier.fillMaxWidth().height(LOADING_HEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(color = palette.ink2, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink2),
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

private val LOADING_HEIGHT = 64.dp

/** Choose another while a version downloads, and Cancel while the request is still going. */
@Composable
private fun LaneActionRow(lane: BookRequest, uiState: BookRequestUIState, actions: RequestActions) {
    val canSwap = lane.state == RequestState.OnItsWay && uiState.viewer.canChoose && isDownloading(lane)
    val canCancel = offersCancel(uiState.viewer, lane)
    if (!canSwap && !canCancel) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (canSwap) {
            RequestButton(
                text = stringResource(if (uiState.busy == Busy.Swapping(lane.id)) R.string.requests_choosing else R.string.requests_choose_another),
                busy = uiState.busy == Busy.Swapping(lane.id),
                onClick = { actions.onSwap(lane.id) },
                emphasis = RequestEmphasis.Ghost,
                enabled = !uiState.isLocked,
            )
        }
        if (canCancel) CancelButton(lane, uiState, actions)
    }
}

@Composable
private fun CancelButton(lane: BookRequest, uiState: BookRequestUIState, actions: RequestActions) {
    val early = lane.state == RequestState.Looking || lane.state == RequestState.Choose
    val label = stringResource(if (early) R.string.requests_cancel_request else R.string.requests_cancel)
    // Another reader's Cancel names whose request it is.
    val description = if (lane.userId == uiState.viewer.id) {
        label
    } else {
        stringResource(
            R.string.requests_cancel_theirs,
            uiState.listingFor(lane)?.requesterName.orEmpty(),
            uiState.book?.displayTitle.orEmpty(),
        )
    }
    RequestButton(
        text = if (uiState.busy == Busy.Cancelling(lane.id)) stringResource(R.string.requests_cancelling) else label,
        busy = uiState.busy == Busy.Cancelling(lane.id),
        onClick = { actions.onCancel(lane.id) },
        emphasis = RequestEmphasis.Ghost,
        enabled = !uiState.isLocked,
        contentDescription = description,
    )
}

@Composable
private fun TryAgain(lane: BookRequest, uiState: BookRequestUIState, actions: RequestActions) {
    RequestButton(
        text = stringResource(if (uiState.busy == Busy.Retrying(lane.id)) R.string.requests_retrying else R.string.requests_retry),
        busy = uiState.busy == Busy.Retrying(lane.id),
        onClick = { actions.onRetry(lane.id) },
        emphasis = RequestEmphasis.Primary,
        enabled = !uiState.isLocked,
    )
}

// Versions

/** The versions found, best first, four at a time. */
@Composable
private fun VersionList(lane: BookRequest, uiState: BookRequestUIState, wording: RequestWording, actions: RequestActions) {
    val palette = OmnibusTheme.palette
    var showAll by rememberSaveable(lane.id) { mutableStateOf(false) }
    val collapsed = firstShown(lane.versions)
    val hidden = lane.versions.size - collapsed.size
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        lane.note?.let { Text(it, style = uiTextStyle(13, FontWeight.Normal, palette.ink2)) }
        Text(
            text = versionsFound(lane.versions, wording).uppercase(),
            style = monoTextStyle(10, palette.ink3).copy(letterSpacing = 1.sp),
        )
        if (!uiState.viewer.canChoose) {
            Text(
                text = stringResource(R.string.requests_waiting_for_chooser),
                style = uiTextStyle(13, FontWeight.Normal, palette.ink2),
            )
        }
        (if (showAll) lane.versions else collapsed).forEach { version ->
            VersionRow(
                version = version,
                wording = wording,
                onChoose = if (uiState.viewer.canChoose) ({ actions.onChoose(lane.id, version.id) }) else null,
                choosing = uiState.busy == Busy.Choosing(lane.id, version.id),
                locked = uiState.isLocked,
            )
        }
        if (hidden > 0) {
            TextLinkButton(
                text = if (showAll) stringResource(R.string.requests_show_fewer) else stringResource(R.string.requests_show_more, hidden),
                color = palette.ink1,
                size = 14,
                onClick = { showAll = !showAll },
            )
        }
    }
}

/** One version found: its name (a link to the source's page when it gave one), its source line and its tags. */
@Composable
private fun VersionRow(
    version: RequestVersion,
    wording: RequestWording,
    onChoose: (() -> Unit)?,
    choosing: Boolean,
    locked: Boolean,
) {
    val palette = OmnibusTheme.palette
    val best = version.bestMatch && !version.rejected
    LaneCard(radius = Radius.md, fill = palette.bg1, accented = best, spacing = 0.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.weight(1f)) {
                VersionName(version)
                Text(versionSource(version, wording), style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2))
                if (version.rejected) {
                    Tag(stringResource(R.string.requests_tag_didnt_work), palette.ink3)
                } else if (best) {
                    Tag(stringResource(R.string.requests_tag_best_match), palette.accent)
                }
                if (convertsToEpub(version) && !version.rejected) {
                    Tag(stringResource(R.string.requests_tag_converts), palette.ink3)
                }
            }
            if (onChoose != null && !version.rejected) {
                RequestButton(
                    text = stringResource(if (choosing) R.string.requests_choosing else R.string.requests_choose),
                    busy = choosing,
                    onClick = onChoose,
                    emphasis = if (best) RequestEmphasis.Primary else RequestEmphasis.Secondary,
                    enabled = !locked,
                    contentDescription = stringResource(R.string.requests_choose_version, version.title),
                )
            }
        }
    }
}

@Composable
private fun VersionName(version: RequestVersion) {
    val palette = OmnibusTheme.palette
    val uriHandler = LocalUriHandler.current
    val page = version.infoUrl?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    // The word joiner keeps the arrow on the name's last line.
    val text = if (page != null) "${version.title}⁠ ↗" else version.title
    val opens = stringResource(R.string.requests_opens_source)
    Text(
        text = text,
        style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
        modifier = if (page != null) {
            // The arrow is only a sign for the eye, so a screen reader hears the name and that it opens the source.
            Modifier
                .clickable(onClickLabel = opens, role = Role.Button) { runCatching { uriHandler.openUri(page) } }
                .semantics { contentDescription = version.title }
        } else {
            Modifier
        },
    )
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text = text.uppercase(), style = monoTextStyle(9.5.sp, color).copy(letterSpacing = 0.8.sp))
}

// Picked, downloading, added

/** The version a request picked: its format and size over its name. [meta] is the line under the name; the pick's size and source when null. */
@Composable
private fun PickedCard(lane: BookRequest, meta: String?) {
    val palette = OmnibusTheme.palette
    val picked = pickedVersion(lane)
    LaneCard(radius = Radius.md, fill = palette.bg1, spacing = 4.dp, modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = picked?.let(::pickedHeading) ?: stringResource(R.string.requests_your_pick),
            style = uiTextStyle(17, FontWeight.SemiBold, palette.ink0),
        )
        picked?.let { Text(it.title, style = uiTextStyle(12, FontWeight.Normal, palette.ink3)) }
        (meta ?: picked?.let(::pickedMeta))?.takeIf { it.isNotEmpty() }?.let {
            Text(it, style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2))
        }
    }
}

/** A download: the pick over its bar, then its percent and time left. */
@Composable
private fun DownloadCard(lane: BookRequest, left: String?) {
    val palette = OmnibusTheme.palette
    val picked = pickedVersion(lane)
    val percent = downloadPercent(lane)
    LaneCard(radius = Radius.md, fill = palette.bg1, spacing = Spacing.sm) {
        Text(
            text = picked?.let(::pickedHeading) ?: stringResource(R.string.requests_your_pick),
            style = uiTextStyle(17, FontWeight.SemiBold, palette.ink0),
        )
        picked?.let { Text(it.title, style = uiTextStyle(12, FontWeight.Normal, palette.ink3)) }
        if ((lane.progress ?: 0.0) >= 1.0) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CircularProgressIndicator(color = palette.ink2, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                Text(stringResource(R.string.requests_adding_to_library), style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink2))
            }
        } else {
            ProgressBar(lane.progress ?: 0.0)
        }
        listOfNotNull(percent?.let { "$it%" }, left).joinToString(SEP).takeIf { it.isNotEmpty() }?.let {
            Text(it, style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2))
        }
    }
}

/** The download's bar. Its fill settles toward the reported fraction, or jumps there under reduced motion. */
@Composable
private fun ProgressBar(fraction: Double) {
    val palette = OmnibusTheme.palette
    val reduced = rememberReducedMotion()
    val shown by androidx.compose.animation.core.animateFloatAsState(
        targetValue = fraction.coerceIn(0.0, 1.0).toFloat(),
        animationSpec = if (reduced) androidx.compose.animation.core.snap() else Motion.settle(),
        label = "requestProgress",
    )
    Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(palette.line2)) {
        Box(Modifier.fillMaxWidth(shown).height(6.dp).clip(CircleShape).background(palette.accent))
    }
}

// Cover offer

/** The sharper cover a finished download brought, beside the book's own, with Use and Keep. */
@Composable
private fun CoverOfferCard(
    lane: BookRequest,
    offer: com.omnibus.omnibus.data.requests.CoverOffer,
    uiState: BookRequestUIState,
    wording: RequestWording,
    actions: RequestActions,
) {
    val palette = OmnibusTheme.palette
    LaneCard(radius = Radius.md, fill = palette.bg1) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.Top) {
            CoverFigure(stringResource(R.string.requests_cover_now)) {
                uiState.book?.let { BookCover(it.coverIdentity(), Modifier.width(COVER_FIGURE_WIDTH)) }
            }
            CoverFigure(stringResource(R.string.requests_cover_from_download)) {
                OfferedCover(lane.id, Modifier.width(COVER_FIGURE_WIDTH))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.requests_cover_sharper), style = uiTextStyle(15, FontWeight.SemiBold, palette.ink0))
            Text(coverSizesLine(offer, wording), style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2))
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            RequestButton(
                text = stringResource(if (uiState.busy == Busy.TakingCover(lane.id)) R.string.requests_cover_using else R.string.requests_cover_use),
                busy = uiState.busy == Busy.TakingCover(lane.id),
                onClick = { actions.onTakeCover(lane.id) },
                emphasis = RequestEmphasis.Primary,
                fullWidth = true,
                enabled = !uiState.isLocked,
            )
            RequestButton(
                text = stringResource(if (uiState.busy == Busy.KeepingCover(lane.id)) R.string.requests_cover_keeping else R.string.requests_cover_keep),
                busy = uiState.busy == Busy.KeepingCover(lane.id),
                onClick = { actions.onKeepCover(lane.id) },
                emphasis = RequestEmphasis.Ghost,
                fullWidth = true,
                enabled = !uiState.isLocked,
            )
        }
    }
}

private val COVER_FIGURE_WIDTH = 64.dp

@Composable
private fun CoverFigure(caption: String, cover: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cover()
        Text(caption, style = uiTextStyle(12, FontWeight.Normal, OmnibusTheme.palette.ink3))
    }
}

// Unrequested

/** A format the book has neither in the library nor among its requests, with a button to ask for it. */
@Composable
internal fun UnrequestedLane(kind: RequestKind, uiState: BookRequestUIState, wording: RequestWording, actions: RequestActions, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(palette.line2, Radius.lg)
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KindChip(kind)
        Text(kindLabel(kind, wording), style = uiTextStyle(16, FontWeight.Medium, palette.ink0))
        Text(
            text = stringResource(R.string.requests_not_requested),
            style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink3),
            modifier = Modifier.weight(1f),
        )
        StartButton(kind, label = stringResource(R.string.requests_request), uiState, wording, actions)
    }
}

/** Starts a request of [kind]: the unrequested lane's Request and a cancelled request's Request again. */
@Composable
internal fun StartButton(kind: RequestKind, label: String, uiState: BookRequestUIState, wording: RequestWording, actions: RequestActions) {
    RequestButton(
        text = if (uiState.busy == Busy.Starting(kind)) stringResource(R.string.requests_requesting) else label,
        busy = uiState.busy == Busy.Starting(kind),
        width = RequestButtonWidth,
        onClick = { actions.onStart(kind) },
        enabled = !uiState.isLocked,
        contentDescription = stringResource(R.string.requests_request_kind, label, kindLabel(kind, wording).lowercase()),
    )
}

/** A dashed hairline around the content, as the design draws an empty slot. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
        ),
    )
}
