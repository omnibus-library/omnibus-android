package com.omnibus.omnibus.feature.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.search.LibrarySearchError
import com.omnibus.omnibus.ui.components.StateAction
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Why a search failed, as the reader is told. */
enum class SearchFailure(
    @param:StringRes val title: Int,
    @param:StringRes val message: Int,
    @param:DrawableRes val glyph: Int,
) {
    Unreachable(R.string.search_unreachable_title, R.string.search_unreachable_message, R.drawable.ic_search_unreachable),
    NotWorking(R.string.search_not_working_title, R.string.search_not_working_message, R.drawable.ic_alert_triangle),
}

/** The failure to show for this error, or null when the app's session handling takes over (401). */
fun LibrarySearchError.toSearchFailure(): SearchFailure? = when (this) {
    LibrarySearchError.Unreachable -> SearchFailure.Unreachable
    is LibrarySearchError.Http, LibrarySearchError.UnexpectedResponse -> SearchFailure.NotWorking
    LibrarySearchError.SessionExpired -> null
}

/** A failed search's full-screen state: its [failure] copy with a "Try again" that calls [onRetry]. */
@Composable
fun SearchErrorState(failure: SearchFailure, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    SearchStateView(
        glyph = failure.glyph,
        message = stringResource(failure.message),
        modifier = modifier,
        title = stringResource(failure.title),
        action = StateAction(stringResource(R.string.search_try_again), onRetry),
    )
}

/**
 * A Search full-screen state: a dashed circle around [glyph], an optional [title], the [message] and an optional
 * [action]. The shape of the Prompt, No matches and failure states in the spec's images.
 */
@Composable
internal fun SearchStateView(
    @DrawableRes glyph: Int,
    message: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    action: StateAction? = null,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier.fillMaxWidth().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                drawCircle(
                    color = palette.ink3,
                    radius = size.minDimension / 2 - 0.5.dp.toPx(),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            Icon(painterResource(glyph), null, tint = palette.ink2, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(24.dp))
        if (title != null) {
            Text(title, style = displayTextStyle(28, palette.ink0), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }
        Text(
            message,
            style = uiTextStyle(if (title == null) 17 else 15, FontWeight.Normal, palette.ink2),
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            SearchRetryButton(action.label, action.onClick)
        }
    }
}

/** The outlined pill under a Search state. Its 48dp height is the touch target (spec: at least 48dp). */
@Composable
private fun SearchRetryButton(label: String, onClick: () -> Unit) {
    val palette = OmnibusTheme.palette
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .border(1.dp, palette.line, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 36.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = uiTextStyle(16, FontWeight.Medium, palette.accentText))
    }
}
