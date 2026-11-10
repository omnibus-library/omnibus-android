package com.omnibus.omnibus.feature.shelves.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Shelf
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.library.ShelfPreview
import com.omnibus.omnibus.data.library.ShelfVisibility
import com.omnibus.omnibus.feature.library.LibraryScreenState
import com.omnibus.omnibus.feature.library.ui.libraryErrorText
import com.omnibus.omnibus.feature.shelves.ShelfOwnerGroup
import com.omnibus.omnibus.feature.shelves.ShelvesCensus
import com.omnibus.omnibus.feature.shelves.ShelvesGridMetrics
import com.omnibus.omnibus.feature.shelves.ShelvesUIState
import com.omnibus.omnibus.feature.shelves.ShelvesViewModel
import com.omnibus.omnibus.ui.components.EmptyStateView
import com.omnibus.omnibus.ui.components.ErrorStateView
import com.omnibus.omnibus.ui.components.LoadingStateView
import com.omnibus.omnibus.ui.components.ReaderAvatar
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.ui.semantics.Role

/** The Shelves screen's route, pushed on the Library tab's stack from the rail's All link. */
@Serializable
data object ShelvesRoute : NavKey

/** Test tags for the Shelves screen. */
internal object ShelvesScreenTestTags {
    const val BACK = "shelves-back"
    const val GRID = "shelves-grid"
    const val TITLE = "shelves-title"
    const val CENSUS = "shelves-census"
    const val NOTICE = "shelves-notice"
    const val EMPTY = "shelves-empty"
    fun group(ownerId: Long) = "shelves-group-$ownerId"
}

/** The separator between the parts of the census line. */
private const val CENSUS_SEPARATOR = " · "

/**
 * The Shelves screen: every shelf the user can see, grouped by owner, under a back button, a title and a census line.
 *
 * Stateful wrapper that feeds [ShelvesScreenContent] from [ShelvesViewModel].
 *
 * @param onBack Called when the back button is tapped.
 */
@Composable
fun ShelvesScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShelvesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Runs each time the entry is composed again, so returning to the screen reloads the list, as the Library does.
    LaunchedEffect(Unit) { viewModel.screenShown() }

    ShelvesScreenContent(
        uiState = uiState,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::retry,
        onTransientErrorShown = viewModel::transientErrorShown,
        modifier = modifier,
    )
}

/**
 * The Shelves screen's state-driven layout, without the ViewModel.
 *
 * Internal rather than private so the instrumented tests can render each state with hand-built [ShelvesUIState]s.
 *
 * @param onRefresh Called when the list is pulled down.
 * @param onRetry Called when the full-screen error's Try again is tapped.
 * @param onTransientErrorShown Called once the snackbar for [ShelvesUIState.transientError] has been launched.
 * @param snackbarHostState Hosts the transient error snackbar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShelvesScreenContent(
    uiState: ShelvesUIState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onTransientErrorShown: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scope = rememberCoroutineScope()
    val transientMessage = uiState.transientError?.let { libraryErrorText(it) }

    // Clearing the message restarts this effect with null, so the snackbar is launched in a scope
    // that outlives the effect.
    LaunchedEffect(uiState.transientError) {
        val message = transientMessage ?: return@LaunchedEffect
        onTransientErrorShown()
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    val edge = Modifier.padding(horizontal = Spacing.screen)
    Box(modifier.fillMaxSize().background(OmnibusTheme.palette.bg0)) {
        Column(Modifier.fillMaxSize()) {
            ShelvesBackButton(
                onBack = onBack,
                modifier = Modifier.padding(start = Spacing.screen, top = Spacing.sm, bottom = Spacing.xs),
            )
            when (val screen = uiState.screen) {
                LibraryScreenState.Loading -> {
                    ShelvesHeader(census = null, showsNotice = false, modifier = edge)
                    LoadingStateView(
                        label = stringResource(R.string.shelves_loading),
                        modifier = Modifier.weight(1f),
                    )
                }

                is LibraryScreenState.Error -> {
                    ShelvesHeader(census = null, showsNotice = false, modifier = edge)
                    ErrorStateView(
                        title = stringResource(R.string.library_error_title),
                        message = libraryErrorText(screen.error),
                        actionLabel = stringResource(R.string.library_try_again),
                        onRetry = onRetry,
                        modifier = Modifier.weight(1f),
                    )
                }

                LibraryScreenState.Empty -> {
                    ShelvesHeader(census = null, showsNotice = false, modifier = edge)
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.weight(1f),
                    ) {
                        EmptyShelves()
                    }
                }

                LibraryScreenState.Content -> PullToRefreshBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.weight(1f),
                ) {
                    ShelvesGrid(uiState)
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
        )
    }
}

/** The round back button, drawn on the screen's ground rather than over cover art, so it has a solid fill. */
@Composable
private fun ShelvesBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.bg1)
            .border(1.dp, palette.line2, CircleShape)
            .clickable(role = Role.Button, onClick = onBack)
            .testTag(ShelvesScreenTestTags.BACK),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_book_detail_back),
            contentDescription = stringResource(R.string.book_detail_back),
            tint = palette.ink0,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The title, then the census line when there is a shelf to count, then the private-shelves notice when [showsNotice].
 * The grid puts it at its top, and the other states put it above their message.
 */
@Composable
private fun ShelvesHeader(census: ShelvesCensus?, showsNotice: Boolean, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.shelves_title),
            style = displayTextStyle(42, palette.ink0),
            modifier = Modifier.semantics { heading() }.testTag(ShelvesScreenTestTags.TITLE),
        )
        if (census != null && census.total > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = shelvesCensusText(census),
                style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
                modifier = Modifier.testTag(ShelvesScreenTestTags.CENSUS),
            )
        }
        if (showsNotice) {
            Row(
                Modifier.padding(top = 18.dp).testTag(ShelvesScreenTestTags.NOTICE),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shelf_private),
                    contentDescription = null,
                    tint = palette.ink3,
                    modifier = Modifier.padding(top = 3.dp).size(14.dp),
                )
                Text(
                    text = stringResource(R.string.shelves_private_notice),
                    style = uiTextStyle(14, FontWeight.Normal, palette.ink2),
                )
            }
        }
    }
}

/**
 * The census line: "N shelves", then "N yours" and "N from N other readers" when they are not zero. The parts are
 * joined with a middle dot.
 */
@Composable
internal fun shelvesCensusText(census: ShelvesCensus): String {
    val parts = buildList {
        add(pluralStringResource(R.plurals.shelves_census_total, census.total, census.total))
        if (census.yours > 0) add(stringResource(R.string.shelves_census_yours, census.yours))
        if (census.fromOthers > 0) {
            add(
                pluralStringResource(
                    R.plurals.shelves_census_from_others,
                    census.otherReaders,
                    census.fromOthers,
                    census.otherReaders,
                ),
            )
        }
    }
    return parts.joinToString(CENSUS_SEPARATOR)
}

/**
 * The grid of tiles, one group per owner. The header, the notice and each group's heading span the whole row, and the
 * column count and cell width come from [ShelvesGridMetrics] for the width left after the screen insets.
 */
@Composable
private fun ShelvesGrid(uiState: ShelvesUIState) {
    val groups = remember(uiState.previews, uiState.viewerId) { uiState.groups }
    val census = remember(uiState.previews, uiState.viewerId) { uiState.census }
    val showsNotice = remember(uiState.previews, uiState.viewerId) { uiState.showsNotice }
    val you = stringResource(R.string.shelves_owner_you)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val available = maxWidth - Spacing.screen * 2
        val columns = ShelvesGridMetrics.columns(available.value)
        val cellWidth = ShelvesGridMetrics.cellWidth(available.value).dp

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize().testTag(ShelvesScreenTestTags.GRID),
        ) {
            item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                ShelvesHeader(census = census, showsNotice = showsNotice)
            }
            groups.forEach { group ->
                item(key = "group-${group.ownerId}", span = { GridItemSpan(maxLineSpan) }) {
                    ShelvesGroupHeading(group, Modifier.padding(top = 16.dp, bottom = 4.dp))
                }
                items(group.shelves, key = { "shelf-${it.shelf.id}" }) { preview ->
                    ShelfTile(
                        preview = preview,
                        ownerLabel = if (group.isViewer) you else group.ownerName,
                        modifier = Modifier.widthIn(max = cellWidth).fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * An owner's heading: their avatar, "Your shelves" or "<name>’s shelves", and the group's count at the trailing edge,
 * over a hairline. It is one heading for TalkBack, so the avatar and the count are not read separately.
 */
@Composable
private fun ShelvesGroupHeading(group: ShelfOwnerGroup, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val title = if (group.isViewer) {
        stringResource(R.string.shelves_group_yours)
    } else {
        stringResource(R.string.shelves_group_other, group.ownerName)
    }
    Row(
        modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { heading() }
            .testTag(ShelvesScreenTestTags.group(group.ownerId))
            .drawBehind {
                drawLine(
                    color = palette.line2,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ReaderAvatar(
            userId = group.ownerId,
            name = group.ownerName,
            hasAvatar = group.ownerHasAvatar,
            size = 30.dp,
        )
        Text(
            text = title,
            style = displayTextStyle(24, palette.ink0),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = group.shelves.size.toString(),
            style = monoTextStyle(11, palette.ink3),
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/**
 * The empty state inside a scroll container, so a pull gesture still reaches the refresh box. It has no action: a shelf
 * is made elsewhere.
 */
@Composable
private fun EmptyShelves() {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = maxHeight
        Column(Modifier.verticalScroll(rememberScrollState()).testTag(ShelvesScreenTestTags.EMPTY)) {
            EmptyStateView(
                title = stringResource(R.string.shelves_empty_title),
                message = stringResource(R.string.shelves_empty_message),
                kicker = stringResource(R.string.shelves_empty_kicker),
                glyph = R.drawable.ic_shelf_manual,
                modifier = Modifier.height(height).fillMaxWidth(),
            )
        }
    }
}

private fun previewShelf(id: Long, name: String, ownerId: Long, ownerName: String, visibility: ShelfVisibility, kind: ShelfKind, bookCount: Long, covers: Int) =
    ShelfPreview(
        shelf = Shelf(
            id = id,
            ownerUserId = ownerId,
            ownerUsername = ownerName,
            kind = kind,
            name = name,
            visibility = visibility,
            accent = null,
            bookCount = bookCount,
        ),
        coverUuids = List(covers) { "preview-$id-$it" },
    )

@PreviewLightDark
@Composable
private fun ShelvesScreenPreview() {
    OmnibusTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            ShelvesScreenContent(
                uiState = ShelvesUIState(
                    previews = listOf(
                        previewShelf(1L, "Compiler corner", 7L, "hunter", ShelfVisibility.Private, ShelfKind.Manual, 3L, 3),
                        previewShelf(2L, "Kobo library", 7L, "hunter", ShelfVisibility.Private, ShelfKind.Smart, 62L, 4),
                        previewShelf(3L, "Cozy mysteries", 8L, "maya", ShelfVisibility.Private, ShelfKind.Manual, 12L, 4),
                    ),
                    viewerId = 7L,
                ),
                onBack = {},
                onRefresh = {},
                onRetry = {},
                onTransientErrorShown = {},
            )
        }
    }
}
