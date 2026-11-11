package com.omnibus.omnibus.feature.requests.page

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.historyDetails
import com.omnibus.omnibus.feature.requests.historyLine
import com.omnibus.omnibus.feature.requests.swapFacts
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The History heading and one row per past request, or nothing when there are none. */
@Composable
internal fun RequestHistory(
    rows: List<RequestListing>,
    uiState: BookRequestUIState,
    wording: RequestWording,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val palette = OmnibusTheme.palette
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = stringResource(R.string.requests_history).uppercase(),
            style = monoTextStyle(10.5.sp, palette.ink3).copy(letterSpacing = 1.2.sp),
            modifier = Modifier.semantics { heading() },
        )
        rows.forEach { HistoryRow(it, uiState, wording) }
    }
}

/** One past request, saying only what was recorded. */
@Composable
private fun HistoryRow(listing: RequestListing, uiState: BookRequestUIState, wording: RequestWording) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.bg2, RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(historyLine(listing, uiState.viewer.id, wording = wording), style = uiTextStyle(14, FontWeight.Normal, palette.ink0))
            historyDetails(listing, wording)?.let { Text(it, style = uiTextStyle(12, FontWeight.Normal, palette.ink3)) }
            swapFacts(listing, wording).forEach {
                Text(it.text, style = uiTextStyle(12, FontWeight.Normal, palette.ink3))
            }
        }
    }
}
