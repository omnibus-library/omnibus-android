package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The Journal list: the heading with its count, then the content for the state. Cards compose a page at a time as the
 * reader scrolls, because the host is a plain scrolling column.
 */
@Composable
internal fun JournalContent(
    state: JournalUIState,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val content = state.content
    val cards = (content as? JournalUIContent.Loaded)?.cards.orEmpty()

    Column(modifier.fillMaxWidth().testTag(JournalTestTags.SECTION).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                stringResource(R.string.book_detail_journal_heading),
                style = displayTextStyle(size = 30, color = palette.ink0, weight = FontWeight.SemiBold),
                modifier = Modifier
                    .alignByBaseline()
                    .semantics { heading() }
                    .testTag(JournalTestTags.HEADING),
            )
            if (content is JournalUIContent.Loaded && cards.isNotEmpty()) {
                Text(
                    cards.size.toString(),
                    style = uiTextStyle(size = 17, weight = FontWeight.Medium, color = palette.ink2),
                    modifier = Modifier
                        .alignByBaseline()
                        .padding(start = 4.dp)
                        .testTag(JournalTestTags.COUNT),
                )
            }
        }

        when (content) {
            JournalUIContent.Loading -> Unit
            JournalUIContent.Failed -> {
                Text(
                    stringResource(R.string.book_detail_journal_failed),
                    style = uiTextStyle(size = 16, weight = FontWeight.Normal, color = palette.ink1),
                    modifier = Modifier.padding(top = 4.dp).testTag(JournalTestTags.FAILED),
                )
                StateActionButton(
                    label = stringResource(R.string.book_detail_journal_retry),
                    onClick = onRetry,
                    modifier = Modifier.padding(top = 12.dp).testTag(JournalTestTags.RETRY),
                )
            }
            is JournalUIContent.Loaded -> {
                if (cards.isEmpty()) {
                    Text(
                        stringResource(R.string.book_detail_journal_empty),
                        style = uiTextStyle(size = 16, weight = FontWeight.Normal, color = palette.ink1)
                            .copy(lineHeight = 1.45.em),
                        modifier = Modifier.padding(top = 4.dp).testTag(JournalTestTags.EMPTY),
                    )
                } else {
                    JournalList(cards, state.nowMillis)
                }
            }
        }
    }
}

/**
 * The cards, composed a page at a time. When the end of the composed cards comes within two window heights of the top of
 * the window, the next page is composed. Scrolling the page moves the sentinel, so composition follows the reader.
 */
@Composable
private fun JournalList(cards: List<JournalCardModel>, nowMillis: Long) {
    var shown by rememberSaveable { mutableIntStateOf(JOURNAL_PAGE) }
    val density = LocalDensity.current
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.takeIf { it > 0 }
        ?: with(density) { LocalConfiguration.current.screenHeightDp.dp.roundToPx() }

    cards.take(shown).forEach { card ->
        key(card.id) {
            JournalCard(card, nowMillis, Modifier.padding(top = 10.dp))
        }
    }
    if (shown < cards.size) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .testTag(JournalTestTags.MORE)
                .onGloballyPositioned { coordinates ->
                    if (coordinates.isAttached && coordinates.positionInWindow().y <= windowHeightPx * 2f) {
                        shown = nextJournalShownCount(shown, cards.size)
                    }
                },
        )
    }
}
