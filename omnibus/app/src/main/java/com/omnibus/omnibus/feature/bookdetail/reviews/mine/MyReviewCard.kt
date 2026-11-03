package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.reviews.reviewPlainText
import com.omnibus.omnibus.feature.bookdetail.reviews.starNumber
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRatingInput
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale

/** Whether the rating block is breathing, which is true only while the review is loading and motion is allowed. */
internal val MyReviewBreathesKey = SemanticsPropertyKey<Boolean>("MyReviewBreathes")
private var SemanticsPropertyReceiver.myReviewBreathes by MyReviewBreathesKey

/**
 * The reader's own review, on the Reviews tab: a heading, the stars they gave, a note, and their text and private notes.
 * The pencil and the prompt to write a review appear only when [onCompose] is set, and the trash can deletes the review
 * after a confirmation. While the review is unknown the stars are dimmed, disabled and breathe, and nothing can be sent.
 *
 * @param onRate Called with the new rating, or null to clear it, from a tap, a drag or an accessibility action.
 * @param onDelete Called once the reader confirms the delete.
 * @param onCompose Opens the review sheet, or null to draw no pencil and no prompt.
 * @param reducedMotion Whether the system asks for no animation, so the loading state does not breathe.
 */
@Composable
internal fun MyReviewCard(
    model: MyReviewModel,
    tint: Color,
    onRate: (Float?) -> Unit,
    onDelete: () -> Unit,
    onCompose: (() -> Unit)?,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val known = model is MyReviewModel.Known
    val review = (model as? MyReviewModel.Known)?.review
    val breathes = model == MyReviewModel.Unknown(UnknownReason.Loading) && !reducedMotion
    val breath = if (breathes) breathingAlpha() else 1f

    val noteText = when (val note = model.note()) {
        MyReviewNote.NotRated -> stringResource(R.string.book_detail_rate_note_unrated)
        is MyReviewNote.Rated -> stringResource(R.string.book_detail_rate_note_rated, starNumber(note.value, locale))
        MyReviewNote.Loading -> stringResource(R.string.book_detail_rate_note_loading)
        MyReviewNote.Offline -> stringResource(R.string.book_detail_rate_note_offline)
        MyReviewNote.Failed -> stringResource(R.string.book_detail_rate_note_failed)
    }
    val inlineSpoiler = stringResource(R.string.book_detail_reviews_inline_spoiler)
    var confirming by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 30.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg1)
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
            .testTag(MyReviewTestTags.CARD),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.book_detail_rate_title),
                style = displayTextStyle(26.sp, palette.ink0, FontWeight.SemiBold),
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
                    .testTag(MyReviewTestTags.HEADING),
            )
            onCompose?.let { compose ->
                HeaderAction(
                    icon = PencilIcon,
                    label = stringResource(
                        if (model.hasWriting) R.string.book_detail_rate_edit else R.string.book_detail_rate_write,
                    ),
                    tint = palette.ink1,
                    enabled = known,
                    tag = MyReviewTestTags.PENCIL,
                    onClick = compose,
                )
            }
            if (model.hasReview) {
                HeaderAction(
                    icon = TrashIcon,
                    label = stringResource(R.string.book_detail_rate_delete),
                    tint = palette.bad,
                    enabled = true,
                    tag = MyReviewTestTags.TRASH,
                    onClick = { confirming = true },
                )
            }
        }

        Column(
            Modifier
                .alpha(breath)
                .semantics { myReviewBreathes = breathes }
                .testTag(MyReviewTestTags.RATING_BLOCK),
        ) {
            StarRatingInput(
                rating = model.rating,
                onRatingChange = onRate,
                tint = tint,
                enabled = known,
                modifier = Modifier.offset(x = (-6).dp),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = noteText,
                style = uiTextStyle(14.sp, FontWeight.Normal, palette.ink1),
                modifier = Modifier.testTag(MyReviewTestTags.NOTE),
            )
        }

        if (review != null) {
            reviewPlainText(review.text, finished = true, inlineSpoiler = inlineSpoiler)?.let { text ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = text,
                    style = uiTextStyle(16.sp, FontWeight.Normal, palette.ink0).copy(lineHeight = 23.sp),
                    modifier = Modifier.testTag(MyReviewTestTags.TEXT),
                )
            }
            review.privateNotes?.let { notes -> reviewPlainText(notes, finished = true, inlineSpoiler = inlineSpoiler) }
                ?.let { notes ->
                    Spacer(Modifier.height(14.dp))
                    PrivateNotesBox(notes)
                }
        }

        if (onCompose != null && model.showsPrompt) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.bg0)
                    .border(1.dp, palette.ink0.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onCompose)
                    .padding(14.dp)
                    .testTag(MyReviewTestTags.PROMPT),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.book_detail_rate_prompt),
                    style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink2),
                )
            }
        }

        if (confirming && model.hasReview) {
            AlertDialog(
                onDismissRequest = { confirming = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirming = false
                            onDelete()
                        },
                        modifier = Modifier.testTag(MyReviewTestTags.DELETE_CONFIRM),
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.bad),
                    ) {
                        Text(stringResource(R.string.book_detail_rate_delete_confirm))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirming = false },
                        modifier = Modifier.testTag(MyReviewTestTags.DELETE_CANCEL),
                        colors = ButtonDefaults.textButtonColors(contentColor = palette.ink0),
                    ) {
                        Text(stringResource(R.string.book_detail_rate_delete_cancel))
                    }
                },
                title = { Text(stringResource(R.string.book_detail_rate_delete_title)) },
                text = { Text(stringResource(R.string.book_detail_rate_delete_message)) },
                containerColor = palette.bg2,
                titleContentColor = palette.ink0,
                textContentColor = palette.ink1,
            )
        }
    }
}

/** The opacity of the loading state, which breathes between 0.55 and 1 about every 0.9 s, reversing. */
@Composable
private fun breathingAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "book_detail_rate_breath")
    return transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 900), repeatMode = RepeatMode.Reverse),
        label = "book_detail_rate_breath",
    ).value
}

/** A 44 dp target with one icon, labelled for accessibility. A disabled one is drawn at 35% opacity. */
@Composable
private fun HeaderAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
    }
}

/** The dashed box around the reader's private notes, with a lock and a line that says only they can see them. */
@Composable
private fun PrivateNotesBox(notes: String) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val half = 0.5.dp.toPx()
                drawRoundRect(
                    color = palette.ink0.copy(alpha = 0.24f),
                    topLeft = Offset(half, half),
                    size = Size(size.width - 2 * half, size.height - 2 * half),
                    cornerRadius = CornerRadius(10.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag(MyReviewTestTags.NOTES_BOX),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = LockIcon,
                contentDescription = null,
                tint = palette.ink1,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = stringResource(R.string.book_detail_rate_private_notes),
                style = uiTextStyle(13.sp, FontWeight.Normal, palette.ink1),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(text = notes, style = uiTextStyle(15.sp, FontWeight.Normal, palette.ink0))
    }
}

/** A trash can, outlined in the current tint. */
private val TrashIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewTrash",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M4 7h16M9 7V4h6v3M6.5 7l1 13h9l1-13M10 11v5M14 11v5"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/** A padlock, for the private notes. */
private val LockIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewLock",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M6 11h12v9H6zM8.5 11V7.5a3.5 3.5 0 0 1 7 0V11"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}

/** A pencil, for writing or editing the review. */
private val PencilIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "ReviewPencil",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(
            pathData = addPathNodes("M4 20h4L19.5 8.5a2.1 2.1 0 0 0-3-3L5 17v3zM14.5 7.5l3 3"),
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        .build()
}
