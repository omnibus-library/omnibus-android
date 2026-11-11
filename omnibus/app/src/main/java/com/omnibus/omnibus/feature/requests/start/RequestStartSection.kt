package com.omnibus.omnibus.feature.requests.start

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailTab
import com.omnibus.omnibus.feature.bookdetail.wishlist.rememberIsWishlistOnly
import com.omnibus.omnibus.feature.requests.RequestButton
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.kindLabel
import com.omnibus.omnibus.feature.requests.rememberRequestWording
import com.omnibus.omnibus.feature.requests.requestSlots
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the request slots. */
internal object RequestStartTestTags {
    const val SLOTS = "request-slots"
    fun button(kind: RequestKind) = "request-slot-${kind.name.lowercase()}"
}

/** The width the Request button keeps in every row, so it doesn't change size with its label. */
internal val RequestSlotButtonWidth = 100.dp

/**
 * A Request button for each kind the library lacks, for a reader who may start requests. It has two hosts that share
 * one implementation: the Files tab, for any book missing a kind, and the stage under the wishlist line, so a wished
 * book offers its buttons without a tab switch. It renders nothing without the permissions or a missing kind, and the
 * stage host steps aside while the Files tab is showing, so the buttons are never twice on screen.
 *
 * A start that succeeds opens the request page in the stack that is showing this page. The ViewModel holds the pending
 * open, so a start that answers while the reader is elsewhere opens it when they return.
 */
internal class RequestStartSection(private val host: Host) : BookDetailSection {

    /** Where the section sits on the page. */
    enum class Host { Files, Stage }

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val book = context.book
        if (host == Host.Stage && (context.tabs.selected == BookDetailTab.Files || !rememberIsWishlistOnly(book))) return
        val viewModel = hiltViewModel<RequestStartViewModel, RequestStartViewModel.Factory>(
            creationCallback = { it.create(book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        val onNavigate by rememberUpdatedState(context.onNavigate)
        val onShowMessage by rememberUpdatedState(context.onShowMessage)

        // The push happens here, while this page is on top of the stack that asked, never from a ViewModel.
        LaunchedEffect(state.pendingOpen) {
            state.pendingOpen?.let { uuid ->
                onNavigate(BookDetailDestination.Request(uuid))
                viewModel.opened()
            }
        }
        LaunchedEffect(state.message) {
            state.message?.let { message ->
                onShowMessage(BookDetailMessage(message.text))
                viewModel.messageShown(message)
            }
        }

        val kinds = requestSlots(book.formats, state.canStart)
        if (kinds.isEmpty()) return
        val wording = rememberRequestWording()
        when (host) {
            Host.Files -> Column(modifier.fillMaxWidth().padding(top = 32.dp)) {
                Text(
                    text = stringResource(R.string.requests_not_in_library),
                    style = displayTextStyle(30.sp, OmnibusTheme.palette.ink0, FontWeight.SemiBold),
                    modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
                )
                RequestSlots(kinds, state.starting, state.isOnline, wording, viewModel::start)
            }
            Host.Stage -> RequestSlots(
                kinds, state.starting, state.isOnline, wording, viewModel::start,
                modifier.padding(top = 16.dp).widthIn(max = 320.dp),
            )
        }
    }
}

/** The Files tab's host. */
internal val RequestFilesSection = RequestStartSection(RequestStartSection.Host.Files)

/** The stage's host, under the wishlist line. */
internal val RequestStageSection = RequestStartSection(RequestStartSection.Host.Stage)

/**
 * One row per kind in [kinds], each with a Request button of one fixed width. Starting disables every button and
 * shows "Requesting…" on the pressed one. Offline, they render disabled.
 */
@Composable
internal fun RequestSlots(
    kinds: List<RequestKind>,
    starting: RequestKind?,
    isOnline: Boolean,
    wording: RequestWording,
    onStart: (RequestKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.bg1)
            .testTag(RequestStartTestTags.SLOTS),
    ) {
        kinds.forEachIndexed { index, kind ->
            if (index > 0) HorizontalDivider(color = palette.line, thickness = 0.5.dp)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = kindLabel(kind, wording),
                    style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
                    modifier = Modifier.weight(1f),
                )
                RequestButton(
                    text = stringResource(if (starting == kind) R.string.requests_requesting else R.string.requests_request),
                    onClick = { onStart(kind) },
                    width = RequestSlotButtonWidth,
                    enabled = starting == null && isOnline,
                    contentDescription = stringResource(R.string.requests_request_kind, stringResource(R.string.requests_request), kindLabel(kind, wording).lowercase()),
                    modifier = Modifier.testTag(RequestStartTestTags.button(kind)),
                )
            }
        }
    }
}
