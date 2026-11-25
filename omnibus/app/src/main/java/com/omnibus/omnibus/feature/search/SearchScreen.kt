package com.omnibus.omnibus.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.Masthead
import com.omnibus.omnibus.ui.components.StateAction
import com.omnibus.omnibus.ui.theme.OmnibusPalette
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import kotlinx.coroutines.launch

/** The Search tab's root. [resetKey] is the shell's re-selection count for the tab (C11); a change clears the query. */
@Composable
fun SearchScreen(
    resetKey: Int,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(resetKey) { viewModel.tabReselected(resetKey) }
    SearchScreenContent(
        uiState = uiState,
        onQueryChange = viewModel::updateQuery,
        onClear = viewModel::clearQuery,
        onRetry = viewModel::retry,
        onNoticeShown = viewModel::noticeShown,
        onOpen = onOpen,
        modifier = modifier,
    )
}

/** The Search screen for [uiState], with the callbacks it raises. [listState] and [snackbarHostState] let tests drive it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SearchScreenContent(
    uiState: SearchUIState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onRetry: () -> Unit,
    onNoticeShown: () -> Unit,
    onOpen: (SearchOpen) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val palette = OmnibusTheme.palette
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val notice = uiState.notice
    val noticeText = notice?.let { stringResource(it.failure.title) }
    val retryLabel = stringResource(R.string.search_retry)
    LaunchedEffect(notice) {
        val text = noticeText ?: return@LaunchedEffect
        onNoticeShown()
        scope.launch {
            val result = snackbarHostState.showSnackbar(text, actionLabel = retryLabel, duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) onRetry()
        }
    }
    // A newer answer replaces whatever the snackbar was about.
    val answered = uiState.body.takeIf { it is SearchBody.Results || it is SearchBody.NoMatches }
    LaunchedEffect(answered) {
        if (answered != null) snackbarHostState.currentSnackbarData?.dismiss()
    }
    // Clearing or re-selecting lands on the Prompt with the masthead in view.
    LaunchedEffect(uiState.body is SearchBody.Prompt) {
        if (uiState.body is SearchBody.Prompt) listState.scrollToItem(0)
    }

    // Scrolling the results dismisses the keyboard. It consumes nothing, so the shell's mini player still sees the scroll.
    val hideKeyboardOnScroll = remember(keyboard, focusManager) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    keyboard?.hide()
                    focusManager.clearFocus()
                }
                return Offset.Zero
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(palette.bg0)) {
        val side = searchColumnSide(maxWidth)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().imePadding().nestedScroll(hideKeyboardOnScroll),
            contentPadding = PaddingValues(start = side, end = side, bottom = 24.dp),
        ) {
            item(key = "masthead") { Masthead(title = stringResource(R.string.search_title)) }
            stickyHeader(key = "field") {
                SearchField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    onClear = onClear,
                    modifier = Modifier.background(palette.bg0).padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                )
            }
            when (val body = uiState.body) {
                is SearchBody.Results -> searchResults(body.sections, body.query, onOpen)
                SearchBody.Prompt -> item(key = "state") {
                    SearchStateView(
                        glyph = R.drawable.ic_tab_search,
                        message = stringResource(R.string.search_prompt_message),
                        modifier = Modifier.padding(top = 96.dp),
                    )
                }
                SearchBody.Searching -> item(key = "state") {
                    LoadingStateView(stringResource(R.string.search_searching), Modifier.padding(top = 52.dp))
                }
                is SearchBody.NoMatches -> item(key = "state") {
                    SearchStateView(
                        glyph = R.drawable.ic_search_no_matches,
                        message = stringResource(R.string.search_no_matches_message, body.query),
                        modifier = Modifier.padding(top = 96.dp),
                        title = stringResource(R.string.search_no_matches_title),
                    )
                }
                is SearchBody.Failed -> item(key = "state") {
                    SearchErrorState(body.failure, onRetry, Modifier.padding(top = 96.dp))
                }
                SearchBody.Ended -> Unit
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .widthIn(max = SEARCH_CONTENT_MAX_WIDTH)
                .padding(12.dp),
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(8.dp),
                containerColor = palette.ink0,
                contentColor = palette.bg0,
                actionColor = if (OmnibusTheme.isDark) OmnibusPalette.Light.accentText else palette.accent,
            )
        }
    }
}
