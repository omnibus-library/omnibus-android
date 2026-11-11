package com.omnibus.omnibus.feature.requests.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.requests.RequestListing
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.requests.RequestGroup
import com.omnibus.omnibus.feature.requests.add.AddRequestSheet
import com.omnibus.omnibus.feature.requests.add.AddRequestViewModel
import com.omnibus.omnibus.feature.requests.RequestGroupKey
import com.omnibus.omnibus.feature.requests.RequestTone
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.countsLine
import com.omnibus.omnibus.feature.requests.groupLabel
import com.omnibus.omnibus.feature.requests.itemLine
import com.omnibus.omnibus.feature.requests.page.requestsErrorText
import com.omnibus.omnibus.feature.requests.rememberRequestWording
import com.omnibus.omnibus.feature.shelves.ui.shelvesBarExpandedHeight
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the Requests list. */
internal object RequestsScreenTestTags {
    const val ADD = "requests-add"
    const val LIST = "requests-list"
    const val EMPTY = "requests-empty"
    fun group(key: RequestGroupKey) = "requests-group-${key.name.lowercase()}"
    fun row(uuid: String) = "requests-row-$uuid"
}

/**
 * Every reader's book requests, grouped by where each book stands, under a large top app bar with the back arrow and a
 * button to request a book. It reads when it is shown and polls while it is on screen.
 *
 * Stateful wrapper that feeds [RequestsScreenContent] from [RequestsViewModel].
 *
 * @param onBack Called when the back arrow is tapped.
 * @param onOpenRequest Called with a book's uuid when its row is tapped, or when a request has been started from the
 * Request a book sheet. The shell pushes the request page onto this screen's own stack.
 */
@Composable
fun RequestsScreen(
    onBack: () -> Unit,
    onOpenRequest: (uuid: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestsViewModel = hiltViewModel(),
    addViewModel: AddRequestViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val addState by addViewModel.uiState.collectAsState()
    var showsSheet by rememberSaveable { mutableStateOf(false) }
    val wording = rememberRequestWording()

    // Each runs when the entry enters composition, so returning to the list reads again and polling stops off screen.
    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(Unit) { viewModel.keepCurrent() }

    // A start that succeeded hides the sheet, then pushes the page from this composed entry, so the push lands on this
    // stack. The list is read again without the push waiting for it.
    LaunchedEffect(addState.pendingOpen) {
        addState.pendingOpen?.let { uuid ->
            showsSheet = false
            onOpenRequest(uuid)
            addViewModel.opened()
            viewModel.refresh()
        }
    }

    RequestsScreenContent(
        uiState = uiState,
        wording = wording,
        onBack = onBack,
        onRetry = viewModel::load,
        onRefresh = viewModel::refresh,
        onToggleFold = viewModel::toggleFold,
        onOpenRequest = onOpenRequest,
        onAdd = {
            addViewModel.updateQuery("")
            showsSheet = true
        },
        modifier = modifier,
    )
    if (showsSheet) {
        AddRequestSheet(
            uiState = addState,
            requested = uiState.requested,
            wording = wording,
            onQueryChange = addViewModel::updateQuery,
            onRequestLibrary = { addViewModel.startFromLibrary(it.uuid) },
            onRequestRecord = { addViewModel.startFromRecord(it, uiState.requested) },
            onOpen = { uuid ->
                showsSheet = false
                onOpenRequest(uuid)
            },
            onClose = { showsSheet = false },
        )
    }
}

/** The list's state-driven layout, without the ViewModel. Internal so screenshot tests can render each state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RequestsScreenContent(
    uiState: RequestsUIState,
    wording: RequestWording,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRefresh: () -> Unit,
    onToggleFold: (RequestGroupKey) -> Unit,
    onOpenRequest: (uuid: String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    topBarState: TopAppBarState = rememberTopAppBarState(),
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val scrollBehavior = if (reducedMotion) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = topBarState, snapAnimationSpec = null)
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior(state = topBarState)
    }
    // Attached inside each PullToRefreshBox, not to the root: the box would take every downward drag, and the bar could
    // not expand back.
    val barScroll = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    val listings = uiState.listings
    Column(modifier.fillMaxSize().background(OmnibusTheme.palette.bg0)) {
        RequestsTopBar(
            showsAdd = uiState.viewer.canStart,
            canAdd = uiState.canRequest,
            onBack = onBack,
            onAdd = onAdd,
            scrollBehavior = scrollBehavior,
        )
        when {
            listings == null && uiState.failure != null -> ErrorStateView(
                title = stringResource(R.string.requests_list_error_title),
                message = requestsErrorText(uiState.failure),
                actionLabel = stringResource(R.string.requests_try_again),
                onRetry = onRetry,
                modifier = Modifier.weight(1f),
            )
            listings == null -> LoadingStateView(
                label = stringResource(R.string.requests_list_loading),
                modifier = Modifier.weight(1f),
            )
            listings.isEmpty() -> PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                EmptyRequests(barScroll)
            }
            else -> PullToRefreshBox(isRefreshing = uiState.isRefreshing, onRefresh = onRefresh, modifier = Modifier.weight(1f)) {
                RequestsList(uiState, wording, onToggleFold, onOpenRequest, barScroll)
            }
        }
    }
}

/** A large bar whose title is the display face, with the Request a book button for a reader who may start one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestsTopBar(
    showsAdd: Boolean,
    canAdd: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val palette = OmnibusTheme.palette
    // The bar reads its title styles from the typography, so the display face is set here, for this bar only.
    MaterialTheme(
        typography = MaterialTheme.typography.copy(
            headlineMedium = displayTextStyle(42, palette.ink0),
            titleLarge = displayTextStyle(22, palette.ink0),
        ),
    ) {
        LargeTopAppBar(
            title = { Text(stringResource(R.string.requests_list_title), modifier = Modifier.semantics { heading() }) },
            navigationIcon = { OmnibusBackButton(onClick = onBack) },
            actions = {
                if (showsAdd) {
                    IconButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.testTag(RequestsScreenTestTags.ADD)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_reader_plus),
                            contentDescription = stringResource(R.string.requests_list_add),
                        )
                    }
                }
            },
            expandedHeight = shelvesBarExpandedHeight(),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = palette.bg0,
                scrolledContainerColor = palette.bg1,
                navigationIconContentColor = palette.ink0,
                titleContentColor = palette.ink0,
                actionIconContentColor = palette.ink0,
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun RequestsList(
    uiState: RequestsUIState,
    wording: RequestWording,
    onToggleFold: (RequestGroupKey) -> Unit,
    onOpenRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    val groups = uiState.groups
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(RequestsScreenTestTags.LIST),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
    ) {
        item(key = "counts") {
            Text(
                text = countsLine(groups, wording),
                style = uiTextStyle(13, FontWeight.Normal, palette.ink2),
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
        groups.forEach { group ->
            val folded = group.key in uiState.folded
            item(key = "header-${group.key}") { GroupHeader(group, folded, wording, onToggleFold) }
            if (!folded) {
                items(group.items, key = { "row-${group.key}-${it.request.id}" }) { item ->
                    RequestRow(item, group.key, wording, onOpenRequest)
                    HorizontalDivider(color = palette.line2, thickness = 0.5.dp)
                }
            }
        }
    }
}

/** A group's label and count, folding its books when tapped. */
@Composable
private fun GroupHeader(group: RequestGroup, folded: Boolean, wording: RequestWording, onToggleFold: (RequestGroupKey) -> Unit) {
    val palette = OmnibusTheme.palette
    val state = stringResource(if (folded) R.string.requests_group_folded else R.string.requests_group_open)
    val hint = stringResource(if (folded) R.string.requests_group_show_hint else R.string.requests_group_hide_hint)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = hint) { onToggleFold(group.key) }
            .padding(top = Spacing.lg, bottom = Spacing.sm)
            .semantics(mergeDescendants = true) {
                heading()
                stateDescription = state
            }
            .testTag(RequestsScreenTestTags.group(group.key)),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_down),
            contentDescription = null,
            tint = palette.ink3,
            modifier = Modifier.size(12.dp).rotate(if (folded) -90f else 0f),
        )
        Text(
            text = groupLabel(group.key, wording).uppercase(),
            style = monoTextStyle(10.5.sp, palette.ink3).copy(letterSpacing = 1.2.sp),
        )
        Text(text = group.items.size.toString(), style = monoTextStyle(10.5.sp, palette.ink3))
    }
}

/** One book on the list: its cover, title, where it stands and a status dot. */
@Composable
private fun RequestRow(item: RequestListing, group: RequestGroupKey, wording: RequestWording, onOpenRequest: (String) -> Unit) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenRequest(item.book.uuid) }
            .padding(vertical = Spacing.sm)
            .semantics(mergeDescendants = true) {}
            .testTag(RequestsScreenTestTags.row(item.book.uuid)),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(item.book.coverIdentity(), Modifier.width(44.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = item.book.displayTitle,
                style = displayTextStyle(19, palette.ink0, FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = itemLine(item, group, wording),
                style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink2),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(7.dp).clip(CircleShape).background(group.tone.color(palette)).clearAndSetSemantics {})
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = null,
            tint = palette.ink3.copy(alpha = CHEVRON_ALPHA),
            modifier = Modifier.size(14.dp).rotate(180f),
        )
    }
}

private const val CHEVRON_ALPHA = 0.7f

/** The palette colour a row's status dot is drawn in. */
internal fun RequestTone.color(palette: OmnibusPalette): Color = when (this) {
    RequestTone.Accent -> palette.accent
    RequestTone.Bad -> palette.bad
    RequestTone.Ok -> palette.ok
    RequestTone.Ink1 -> palette.ink1
    RequestTone.Ink3 -> palette.ink3
}

/** The empty list, inside a scroll so pulling to refresh works here too. */
@Composable
private fun EmptyRequests(modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.verticalScroll(rememberScrollState()).testTag(RequestsScreenTestTags.EMPTY)) {
            EmptyStateView(
                title = stringResource(R.string.requests_list_empty_title),
                message = stringResource(R.string.requests_list_empty_message),
                modifier = Modifier.height(height).fillMaxWidth(),
            )
        }
    }
}
