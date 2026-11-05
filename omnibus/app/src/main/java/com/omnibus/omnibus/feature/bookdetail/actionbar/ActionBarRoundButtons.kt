package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The Listen bar's scale, read by tests. A custom key, so accessibility services never see it. */
internal val ListenBarScaleKey = SemanticsPropertyKey<Float>("ListenBarScale")

/** The four Listen bars' resting heights, as the design draws them, from the left. */
internal val ListenBarRest = listOf(0.3f, 1f, 0.55f, 0.8f)

/**
 * The round buttons beside the main button, in the order of [actions]: Read or Listen, then Immersive read. Each is 52 dp
 * and circular. A disabled Listen is dimmed and does nothing on a tap.
 *
 * A tap on an enabled Listen calls [onListen] first and then pulses its bars, so the pulse never delays the action.
 * With [reducedMotion] the bars never move.
 */
@Composable
internal fun RowScope.RoundButtons(
    actions: List<RoundAction>,
    listenColors: ListenButtonColors,
    onRead: (RoundAction.Read) -> Unit,
    onListen: (RoundAction.Listen) -> Unit,
    onImmersiveRead: (RoundAction.ImmersiveRead) -> Unit,
    reducedMotion: Boolean,
) {
    actions.forEach { action ->
        when (action) {
            is RoundAction.Read -> ReadRoundButton(onClick = { onRead(action) })
            is RoundAction.Listen -> ListenRoundButton(action, listenColors, onListen, reducedMotion)
            is RoundAction.ImmersiveRead -> ImmersiveReadRoundButton(onClick = { onImmersiveRead(action) })
        }
    }
}

/** The shared 52 dp circle: a wash, an optional 1 dp rim, and the content centred inside. */
@Composable
private fun RoundButton(
    description: String,
    enabled: Boolean,
    tag: String,
    wash: Color,
    rim: Color?,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .size(52.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .clip(CircleShape)
            .background(wash)
            .then(if (rim != null) Modifier.border(1.dp, rim, CircleShape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .testTag(tag),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Read: a faint ink wash and the open book, shown when the main button is listening. */
@Composable
private fun ReadRoundButton(onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    RoundButton(
        description = stringResource(R.string.book_detail_listen_read),
        enabled = true,
        tag = ActionBarTestTags.ROUND_READ,
        wash = palette.ink0.copy(alpha = 0.1f),
        rim = null,
        onClick = onClick,
    ) {
        Icon(OpenBookIcon, contentDescription = null, tint = palette.ink0, modifier = Modifier.size(21.dp))
    }
}

/** Immersive read: a faint ink wash and the book with sound waves. It is never dimmed. */
@Composable
private fun ImmersiveReadRoundButton(onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    RoundButton(
        description = stringResource(R.string.book_detail_listen_immersive_read),
        enabled = true,
        tag = ActionBarTestTags.ROUND_IMMERSIVE,
        wash = palette.ink0.copy(alpha = 0.1f),
        rim = null,
        onClick = onClick,
    ) {
        Icon(ImmersiveReadIcon, contentDescription = null, tint = palette.ink0, modifier = Modifier.size(21.dp))
    }
}

/**
 * Listen: the book's accent wash with its rim, and four bars. An enabled tap pulses the bars three times, unless
 * [reducedMotion] is set. The bars return to rest when the pulse ends or is cancelled.
 */
@Composable
private fun ListenRoundButton(
    action: RoundAction.Listen,
    colors: ListenButtonColors,
    onListen: (RoundAction.Listen) -> Unit,
    reducedMotion: Boolean,
) {
    val palette = OmnibusTheme.palette
    val bars = remember { ListenBarRest.map { Animatable(it) } }
    val scope = rememberCoroutineScope()
    var pulse by remember { mutableStateOf<Job?>(null) }
    RoundButton(
        description = stringResource(
            if (action.enabled) R.string.book_detail_listen_listen else R.string.book_detail_listen_listen_offline,
        ),
        enabled = action.enabled,
        tag = ActionBarTestTags.ROUND_LISTEN,
        wash = colors.wash,
        rim = colors.rim,
        onClick = {
            onListen(action)
            if (!reducedMotion) {
                pulse?.cancel()
                pulse = scope.launch {
                    bars.forEachIndexed { index, bar ->
                        launch {
                            try {
                                delay(index * 90L)
                                bar.snapTo(0.2f)
                                repeat(3) {
                                    bar.animateTo(1f, tween(310, easing = EaseInOut))
                                    bar.animateTo(0.2f, tween(310, easing = EaseInOut))
                                }
                            } finally {
                                withContext(NonCancellable) { bar.snapTo(ListenBarRest[index]) }
                            }
                        }
                    }
                }
            }
        },
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.height(16.dp),
        ) {
            bars.forEachIndexed { index, bar ->
                val scale = bar.value
                Box(
                    Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            scaleY = scale
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.ink0)
                        .semantics { this[ListenBarScaleKey] = scale }
                        .testTag("${ActionBarTestTags.LISTEN_BAR}-$index"),
                )
            }
        }
    }
}
