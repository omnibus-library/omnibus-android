package com.omnibus.omnibus.feature.requests.page

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.requests.RequestsError
import com.omnibus.omnibus.data.requests.RequestKind
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailPaneLayout
import com.omnibus.omnibus.feature.bookdetail.ui.PaneSplit
import com.omnibus.omnibus.feature.bookdetail.ui.bookDetailPaneSplit
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.rememberRequestWording
import com.omnibus.omnibus.feature.requests.wishersLine
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The widest the lanes' column grows in one column, so a Choose button stays beside its title on a wide, tall window. */
internal val RequestColumnMaxWidth = 600.dp

/** Test tags for the request page. */
internal object BookRequestTestTags {
    const val BACK = "request-back"
    const val OPEN_BOOK = "request-open-book"
    const val HEADER_PANE = "request-header-pane"
    const val LANES_PANE = "request-lanes-pane"
    fun lane(kind: RequestKind) = "request-lane-${kind.name.lowercase()}"
}

/**
 * A book's requests on one page: the book, a lane for the request it opened on and each other one going or stuck, the
 * formats not yet requested, and the history. It reads when it is shown and polls while it is on screen.
 *
 * Stateful wrapper that feeds [BookRequestScreenContent] from [BookRequestViewModel].
 *
 * @param onBack Called when the back arrow is tapped.
 * @param onOpenBook Called by Open book page.
 */
@Composable
fun BookRequestScreen(
    uuid: String,
    onBack: () -> Unit,
    onOpenBook: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookRequestViewModel = hiltViewModel<BookRequestViewModel, BookRequestViewModel.Factory>(
        creationCallback = { it.create(uuid) },
    ),
) {
    val uiState by viewModel.uiState.collectAsState()
    val actions = remember(viewModel) {
        RequestActions(
            onChoose = viewModel::choose,
            onSwap = viewModel::swap,
            onRetry = viewModel::retry,
            onTakeCover = viewModel::takeCover,
            onKeepCover = viewModel::keepCover,
            onStart = viewModel::start,
            onCancel = viewModel::cancel,
            onUndo = viewModel::undo,
        )
    }

    // Each runs when the entry enters composition, so returning to the page reads again and polling stops off screen.
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) { viewModel.keepCurrent() }

    BookRequestScreenContent(
        uiState = uiState,
        wording = rememberRequestWording(),
        onBack = onBack,
        onOpenBook = onOpenBook,
        onRetry = viewModel::load,
        actions = actions,
        onMessageDismissed = viewModel::messageDismissed,
        now = viewModel::nowMillis,
        modifier = modifier,
    )
}

/**
 * The request page's state-driven layout, without the ViewModel. Internal so screenshot tests can render each state
 * with a hand-built [BookRequestUIState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookRequestScreenContent(
    uiState: BookRequestUIState,
    wording: RequestWording,
    onBack: () -> Unit,
    onOpenBook: () -> Unit,
    onRetry: () -> Unit,
    actions: RequestActions,
    onMessageDismissed: (RequestMessage) -> Unit,
    modifier: Modifier = Modifier,
    now: () -> Long = { uiState.nowMillis },
) {
    val palette = OmnibusTheme.palette
    // Measured over the whole window, top bar included, so the split is the book page's rule on the same window.
    BoxWithConstraints(modifier.fillMaxSize().background(palette.bg0)) {
        val split = bookDetailPaneSplit(maxWidth, maxHeight)
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {},
                navigationIcon = { OmnibusBackButton(onClick = onBack, modifier = Modifier.testTag(BookRequestTestTags.BACK)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = palette.bg0,
                    scrolledContainerColor = palette.bg0,
                    navigationIconContentColor = palette.ink0,
                ),
            )
            Box(Modifier.weight(1f)) {
                val book = uiState.book
                when {
                    uiState.isMissing -> EmptyStateView(
                        title = stringResource(R.string.requests_page_not_found_title),
                        message = stringResource(R.string.requests_page_not_found_message),
                    )
                    uiState.failure != null -> ErrorStateView(
                        title = stringResource(R.string.requests_page_error_title),
                        message = requestsErrorText(uiState.failure),
                        actionLabel = stringResource(R.string.requests_try_again),
                        onRetry = onRetry,
                    )
                    book == null -> LoadingStateView(label = stringResource(R.string.requests_page_loading))
                    else -> RequestPage(book, uiState, wording, actions, split, onOpenBook)
                }
                uiState.message?.let { message ->
                    RequestMessageBar(
                        message = message,
                        now = now,
                        canUndo = !uiState.isLocked,
                        onUndo = actions.onUndo,
                        onDismissed = { onMessageDismissed(message) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.md),
                    )
                }
            }
        }
    }
}

/** What a failed read says, by why it failed, in the library screens' own words so the same failure reads the same. */
@Composable
internal fun requestsErrorText(error: RequestsError): String = when (error) {
    RequestsError.Unreachable -> stringResource(R.string.library_error_unreachable)
    RequestsError.SessionExpired -> stringResource(R.string.library_error_session_expired)
    is RequestsError.Http -> error.message ?: stringResource(R.string.library_error_http, error.code)
    RequestsError.UnexpectedResponse -> stringResource(R.string.library_error_unexpected_response)
}

/**
 * The page's two panes: the header on the leading side, the lanes and history on the trailing side, each scrolling on
 * its own. It splits as the book page does, and in one column it is the same composition with one scroll, so nothing
 * that holds state (such as Show more) is lost when a fold opens.
 */
@Composable
private fun RequestPage(
    book: Book,
    uiState: BookRequestUIState,
    wording: RequestWording,
    actions: RequestActions,
    split: PaneSplit?,
    onOpenBook: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val pageScroll = rememberScrollState()
        val headerScroll = rememberScrollState()
        val lanesScroll = rememberScrollState()
        val twoPane = split != null
        BookDetailPaneLayout(
            split = split,
            trailingSpace = 0.dp,
            overhang = 0.dp,
            // In one column the page only scrolls: filling the window would make the layout taller than its content, and the
            // scroll would centre the content in the extra height.
            modifier = if (twoPane) Modifier.fillMaxSize() else Modifier.fillMaxWidth().verticalScroll(pageScroll),
            stagePane = {
                Pane(if (twoPane) headerScroll else null, Modifier.testTag(BookRequestTestTags.HEADER_PANE)) {
                    RequestHeader(book, uiState, wording, onOpenBook)
                }
            },
            tabHost = {
                Pane(if (twoPane) lanesScroll else null, Modifier.testTag(BookRequestTestTags.LANES_PANE)) {
                    uiState.lanes.forEach { RequestLane(it, uiState, wording, actions) }
                    uiState.unrequested.forEach { UnrequestedLane(it, uiState, wording, actions) }
                    RequestHistory(uiState.history, uiState, wording, actions)
                }
            },
        )
    }
}

/** One pane: its content in a column no wider than [RequestColumnMaxWidth], centred, scrolling when [scroll] is given. */
@Composable
private fun Pane(scroll: ScrollState?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .then(if (scroll != null) Modifier.fillMaxHeight().verticalScroll(scroll) else Modifier),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = RequestColumnMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) { content() }
    }
}

/** The book: its cover, title and author, who wishes for it, and the way to its page. */
@Composable
private fun RequestHeader(book: Book, uiState: BookRequestUIState, wording: RequestWording, onOpenBook: () -> Unit) {
    val palette = OmnibusTheme.palette
    val wishers = wishersLine(uiState.wanters.orEmpty(), uiState.viewer.id, wording)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
        BookCover(book.coverIdentity(), Modifier.width(COVER_WIDTH))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = book.displayTitle,
                style = displayTextStyle(32, palette.ink0),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            if (book.creators.isNotEmpty()) {
                Text(book.authorDisplay, style = uiTextStyle(14, FontWeight.Normal, palette.ink2))
            }
            wishers?.let { Text(it, style = uiTextStyle(13, FontWeight.Normal, palette.ink2)) }
            TextLinkButton(
                text = stringResource(R.string.requests_open_book_page),
                color = palette.accent,
                size = 14,
                onClick = onOpenBook,
                modifier = Modifier.testTag(BookRequestTestTags.OPEN_BOOK),
            )
        }
    }
}

private val COVER_WIDTH: Dp = 78.dp
