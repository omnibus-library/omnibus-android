package com.omnibus.omnibus.feature.series.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.series.SeriesDetailsScreenState
import com.omnibus.omnibus.feature.series.SeriesDetailsUIState
import com.omnibus.omnibus.feature.series.SeriesDetailsViewModel
import com.omnibus.omnibus.feature.series.SeriesEntry
import com.omnibus.omnibus.feature.series.SeriesRefreshFailure
import com.omnibus.omnibus.feature.shell.RailBreakpoint
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.OmnibusBackButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch

/** Tags the Series Details page's parts for instrumented tests. */
internal object SeriesDetailsTestTags {
    const val TITLE = "series-details-title"
    const val BACK = "series-details-back"
    const val LIST = "series-details-list"
    const val COUNT_LINE = "series-details-count-line"

    fun book(uuid: String) = "series-details-book-$uuid"

    fun missing(first: Long) = "series-details-missing-$first"
}

/** The page's column width on a wide window. */
internal val SeriesContentMaxWidth = 560.dp

/** The column cap for a window [windowWidth] wide: [SeriesContentMaxWidth] from the rail's breakpoint up, else none. */
internal fun seriesContentMaxWidth(windowWidth: Dp): Dp? =
    if (windowWidth >= RailBreakpoint) SeriesContentMaxWidth else null

/**
 * One series' Series Details page. It loads when it opens and reads its state from [SeriesDetailsViewModel]; the
 * ViewModel is kept for each push, so a page keeps its own state while it is on the back stack.
 *
 * @param seriesId The series to show.
 * @param name The series' name from the link, shown until the page loads.
 * @param onBack Called when the back arrow is tapped.
 * @param onOpenBook Called with the uuid of a book row that is tapped.
 */
@Composable
fun SeriesDetailsScreen(
    seriesId: Long,
    name: String,
    onBack: () -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = hiltViewModel<SeriesDetailsViewModel, SeriesDetailsViewModel.Factory>(
        creationCallback = { it.create(seriesId, name) },
    )
    val uiState by viewModel.uiState.collectAsState()

    SeriesDetailsContent(
        uiState = uiState,
        onBack = onBack,
        onOpenBook = onOpenBook,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onRefreshFailureShown = viewModel::refreshFailureShown,
        modifier = modifier,
    )
}

/**
 * The page's content for [uiState]: the top bar, then the state's view or the list. A failed refresh shows a snackbar
 * with a Retry that refreshes again.
 *
 * @param windowWidth The window's width, which picks the column's width. Tests and previews pass one.
 * @param onRefresh Called by pull to refresh and by the snackbar's Retry.
 * @param onRetry Called by the full-screen states' Try again.
 * @param onRefreshFailureShown Called once the snackbar for [SeriesDetailsUIState.refreshFailure] is shown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SeriesDetailsContent(
    uiState: SeriesDetailsUIState,
    onBack: () -> Unit,
    onOpenBook: (uuid: String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onRefreshFailureShown: () -> Unit,
    modifier: Modifier = Modifier,
    windowWidth: Dp = LocalWindowInfo.current.containerDpSize.width,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val palette = OmnibusTheme.palette
    val scope = rememberCoroutineScope()
    val refreshMessage = when (uiState.refreshFailure) {
        null -> null
        SeriesRefreshFailure.Unreachable -> stringResource(R.string.series_details_snackbar_unreachable)
        SeriesRefreshFailure.WentWrong -> stringResource(R.string.series_details_snackbar_went_wrong)
    }
    val retryLabel = stringResource(R.string.series_details_retry)
    // Clearing the failure restarts this effect with null, so the snackbar runs in a scope that outlives it.
    LaunchedEffect(refreshMessage) {
        val message = refreshMessage ?: return@LaunchedEffect
        onRefreshFailureShown()
        scope.launch {
            if (snackbarHostState.showSnackbar(message, actionLabel = retryLabel) == SnackbarResult.ActionPerformed) {
                onRefresh()
            }
        }
    }

    val contentWidth = seriesContentMaxWidth(windowWidth)
    Box(modifier.fillMaxSize().background(palette.bg0), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxHeight()
                .then(contentWidth?.let { Modifier.widthIn(max = it) } ?: Modifier)
                .fillMaxWidth(),
        ) {
            SeriesDetailsTopBar(title = uiState.name, onBack = onBack)

            when (uiState.screen) {
                SeriesDetailsScreenState.Loading -> LoadingStateView(
                    label = stringResource(R.string.series_details_loading),
                    modifier = Modifier.weight(1f),
                )

                SeriesDetailsScreenState.Unreachable -> ErrorStateView(
                    title = stringResource(R.string.series_details_unreachable_title),
                    message = stringResource(R.string.series_details_unreachable_body),
                    actionLabel = stringResource(R.string.series_details_try_again),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                    actionModifier = Modifier.minimumInteractiveComponentSize(),
                )

                SeriesDetailsScreenState.WentWrong -> ErrorStateView(
                    title = stringResource(R.string.series_details_went_wrong_title),
                    message = stringResource(R.string.series_details_went_wrong_body),
                    actionLabel = stringResource(R.string.series_details_try_again),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                    actionModifier = Modifier.minimumInteractiveComponentSize(),
                )

                SeriesDetailsScreenState.SessionExpired -> ErrorStateView(
                    title = stringResource(R.string.series_details_went_wrong_title),
                    message = stringResource(R.string.library_error_session_expired),
                    actionLabel = stringResource(R.string.series_details_try_again),
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                    actionModifier = Modifier.minimumInteractiveComponentSize(),
                )

                SeriesDetailsScreenState.NotFound -> EmptyStateView(
                    title = stringResource(R.string.series_details_not_found_title),
                    message = stringResource(R.string.series_details_not_found_body),
                    modifier = Modifier.weight(1f),
                )

                SeriesDetailsScreenState.Loaded, SeriesDetailsScreenState.Empty -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    LazyColumn(Modifier.fillMaxSize().testTag(SeriesDetailsTestTags.LIST)) {
                        if (uiState.screen == SeriesDetailsScreenState.Empty) {
                            item {
                                EmptyStateView(
                                    title = stringResource(R.string.series_details_empty_title),
                                    message = stringResource(R.string.series_details_empty_body, uiState.name),
                                    modifier = Modifier.fillParentMaxSize(),
                                )
                            }
                        } else {
                            item {
                                CountLine(author = uiState.countAuthor, count = uiState.books.orEmpty().size)
                            }
                            items(
                                items = uiState.entries,
                                key = { entry ->
                                    when (entry) {
                                        is SeriesEntry.BookEntry -> "book-${entry.book.id}"
                                        is SeriesEntry.Missing -> "missing-${entry.first}"
                                    }
                                },
                            ) { entry ->
                                when (entry) {
                                    is SeriesEntry.BookEntry -> BookRow(book = entry.book, onClick = { onOpenBook(entry.book.uuid) })
                                    is SeriesEntry.Missing -> MissingRow(entry = entry)
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}

/** The top bar: the back arrow and the series' name, which is a heading for TalkBack. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeriesDetailsTopBar(title: String, onBack: () -> Unit) {
    val palette = OmnibusTheme.palette
    TopAppBar(
        title = {
            Text(
                text = title,
                style = displayTextStyle(22, palette.ink0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(SeriesDetailsTestTags.TITLE).semantics { heading() },
            )
        },
        navigationIcon = {
            OmnibusBackButton(onClick = onBack, modifier = Modifier.testTag(SeriesDetailsTestTags.BACK))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = palette.bg0,
            scrolledContainerColor = palette.bg0,
            navigationIconContentColor = palette.ink0,
            titleContentColor = palette.ink0,
            actionIconContentColor = palette.ink0,
        ),
    )
}

/** The count line under the bar: "Author · 3 books", or just the count when there is no author. */
@Composable
private fun CountLine(author: String?, count: Int) {
    val palette = OmnibusTheme.palette
    val countText = pluralStringResource(R.plurals.series_details_book_count, count, count)
    val text = if (author == null) countText else stringResource(R.string.series_details_count_line, author, countText)
    Text(
        text = text,
        style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
        modifier = Modifier
            .testTag(SeriesDetailsTestTags.COUNT_LINE)
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    )
}
