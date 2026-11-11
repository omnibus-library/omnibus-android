package com.omnibus.omnibus.feature.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.needsYouPill
import com.omnibus.omnibus.feature.requests.rememberRequestWording
import com.omnibus.omnibus.feature.shell.ui.UnderConstructionPage
import com.omnibus.omnibus.feature.shell.ui.UnderConstructionScreen
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the You root. */
internal object YouScreenTestTags {
    const val REQUESTS_ROW = "you-requests-row"
    const val REQUESTS_PILL = "you-requests-pill"
}

/**
 * The You tab's root: the Requests row with its "N needs you" pill, when the server has book requests on for the reader.
 * Without that the tab keeps its under-construction page.
 *
 * Stateful wrapper that feeds [YouScreenContent] from [YouViewModel].
 *
 * @param onOpenRequests Called when the Requests row is tapped.
 */
@Composable
fun YouScreen(
    onOpenRequests: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the You root enters composition, so the count is read again on return from Requests too.
    LaunchedEffect(Unit) { viewModel.shown() }

    YouScreenContent(uiState, rememberRequestWording(), onOpenRequests, modifier)
}

/** The You root's layout, without the ViewModel. Internal so screenshot tests can render each state. */
@Composable
internal fun YouScreenContent(
    uiState: YouUIState,
    wording: RequestWording,
    onOpenRequests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Nothing while the session is unknown, so the placeholder never flashes before the row.
    if (uiState.showsRequests == true) {
        Column(modifier.fillMaxSize().padding(horizontal = Spacing.screen, vertical = Spacing.lg)) {
            RequestsRow(needsYouPill(uiState.needsYou, wording), onOpenRequests)
        }
    } else if (uiState.showsRequests == false) {
        UnderConstructionScreen(UnderConstructionPage.You, modifier.fillMaxSize())
    }
}

/** The Requests row, with the pill when books wait on the reader. */
@Composable
private fun RequestsRow(pill: String?, onOpenRequests: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onOpenRequests)
            .semantics(mergeDescendants = true) {}
            .testTag(YouScreenTestTags.REQUESTS_ROW),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.you_requests_row),
            style = uiTextStyle(16, FontWeight.Medium, palette.ink0),
            modifier = Modifier.weight(1f),
        )
        if (pill != null) {
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(palette.accent.copy(alpha = PILL_ALPHA))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
                    .testTag(YouScreenTestTags.REQUESTS_PILL),
            ) {
                Text(pill, style = uiTextStyle(12.5.sp, FontWeight.Medium, palette.accent))
            }
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(16.dp).rotate(180f),
        )
    }
}

private const val PILL_ALPHA = 0.16f
