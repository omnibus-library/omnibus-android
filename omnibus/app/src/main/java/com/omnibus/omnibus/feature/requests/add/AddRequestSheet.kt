package com.omnibus.omnibus.feature.requests.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.requests.LibraryHit
import com.omnibus.omnibus.data.requests.ProviderBook
import com.omnibus.omnibus.feature.requests.AddAction
import com.omnibus.omnibus.feature.requests.RequestButton
import com.omnibus.omnibus.feature.requests.RequestButtonWidth
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.requests.addAction
import com.omnibus.omnibus.feature.requests.providersHeading
import com.omnibus.omnibus.ui.components.BookCover
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for the Request a book sheet. */
internal object AddRequestTestTags {
    const val SHEET = "add-request-sheet"
    const val FIELD = "add-request-field"
    const val RESULTS = "add-request-results"
    const val MESSAGE = "add-request-message"
    fun request(uuidOrTitle: String) = "add-request-request-$uuidOrTitle"
    fun open(uuid: String) = "add-request-open-$uuid"
}

/** How much of the window's height the sheet takes, so it doesn't jump between its idle and results states. */
private const val SHEET_HEIGHT_FRACTION = 0.92f

/** The widest the sheet's content grows, so a row's button stays beside its title on a wide window. */
private val SheetContentMaxWidth = 600.dp

/**
 * The Request a book bottom sheet. Closing is held off while a start runs, so it can't be dismissed on top of one.
 *
 * @param requested The books with an ebook request going, which a row opens rather than starts again.
 * @param onOpen Opens the request page of a book whose request is already going.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddRequestSheet(
    uiState: AddRequestUIState,
    requested: Set<String>,
    wording: RequestWording,
    onQueryChange: (String) -> Unit,
    onRequestLibrary: (LibraryHit) -> Unit,
    onRequestRecord: (ProviderBook) -> Unit,
    onOpen: (uuid: String) -> Unit,
    onClose: () -> Unit,
) {
    val palette = OmnibusTheme.palette
    // Back and the scrim still reach the sheet while a start runs, and `confirmValueChange` and `onDismissRequest`
    // refuse them. The sheet state keeps the lambda it was created with, so it reads the latest value through this.
    val starting by rememberUpdatedState(uiState.starting)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !starting },
    )
    ModalBottomSheet(
        onDismissRequest = { if (!uiState.starting) onClose() },
        sheetState = sheetState,
        sheetGesturesEnabled = !uiState.starting,
        containerColor = palette.bg0,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        modifier = Modifier.testTag(AddRequestTestTags.SHEET),
    ) {
        AddRequestSheetContent(uiState, requested, wording, onQueryChange, onRequestLibrary, onRequestRecord, onOpen, onClose)
    }
}

/**
 * The sheet's content: the title and Cancel, the field with its hint, then the results. Internal so screenshot tests can
 * render each state without a sheet. Its scroll column fills the sheet's width and carries its own ground, so a
 * results column as narrow as its hint can't leave strips of the sheet showing at either edge.
 */
@Composable
internal fun AddRequestSheetContent(
    uiState: AddRequestUIState,
    requested: Set<String>,
    wording: RequestWording,
    onQueryChange: (String) -> Unit,
    onRequestLibrary: (LibraryHit) -> Unit,
    onRequestRecord: (ProviderBook) -> Unit,
    onOpen: (uuid: String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    // The message node only exists while a message is set, which a live region can miss, so each one is announced.
    val view = LocalView.current
    LaunchedEffect(uiState.message) { uiState.message?.let(view::announceForAccessibility) }
    Box(modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION).background(palette.bg0), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = SheetContentMaxWidth).fillMaxWidth().fillMaxHeight().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.requests_add_title),
                    style = displayTextStyle(26, palette.ink0),
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                TextLinkButton(
                    text = stringResource(R.string.requests_add_cancel),
                    color = if (uiState.starting) palette.ink3 else palette.accent,
                    size = 15,
                    onClick = onClose,
                    enabled = !uiState.starting,
                )
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screen)) {
                OmnibusTextField(
                    value = uiState.query,
                    onValueChange = onQueryChange,
                    placeholder = stringResource(R.string.requests_add_field),
                    modifier = Modifier.testTag(AddRequestTestTags.FIELD),
                )
                Text(
                    text = stringResource(R.string.requests_add_hint),
                    style = uiTextStyle(12.5.sp, FontWeight.Normal, palette.ink3),
                    modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.sm),
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.lg)
                    .testTag(AddRequestTestTags.RESULTS),
                verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            ) {
                uiState.message?.let {
                    Text(
                        text = it,
                        style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.bad),
                        modifier = Modifier.fillMaxWidth().testTag(AddRequestTestTags.MESSAGE),
                    )
                }
                if (uiState.isIdle) {
                    Text(
                        text = stringResource(R.string.requests_add_idle),
                        style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink3),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LibrarySection(uiState, requested, onRequestLibrary, onOpen)
                    ProviderSection(uiState, requested, wording, onRequestRecord, onOpen)
                }
            }
        }
    }
}

@Composable
private fun LibrarySection(
    uiState: AddRequestUIState,
    requested: Set<String>,
    onRequest: (LibraryHit) -> Unit,
    onOpen: (String) -> Unit,
) {
    Section(stringResource(R.string.requests_add_in_library), uiState.library) { hit ->
        val action = addAction(hit.uuid, hit.formats, hasFiles = true, requested = requested)
        ResultRow(
            cover = { LibraryCover(hit) },
            title = hit.title,
            byline = byline(listOf(hit.authorDisplay, hit.year), action),
            action = action,
            starting = uiState.starting,
            startingThis = uiState.startingKey == hit.uuid,
            isOnline = uiState.isOnline,
            onRequest = { onRequest(hit) },
            onOpen = { onOpen(hit.uuid) },
            testKey = hit.uuid,
        )
    }
}

@Composable
private fun ProviderSection(
    uiState: AddRequestUIState,
    requested: Set<String>,
    wording: RequestWording,
    onRequest: (ProviderBook) -> Unit,
    onOpen: (String) -> Unit,
) {
    val heading = (uiState.providers as? SearchResults.Done)?.items.orEmpty().map { it.source }
    Section(providersHeading(heading, wording), uiState.providers) { book ->
        // A bare provider record always starts: the library book it matches is only known once it is resolved.
        ResultRow(
            cover = { ProviderCover(book) },
            title = book.title,
            byline = byline(listOf(book.authors.joinToString(", "), book.year), AddAction.Start),
            action = AddAction.Start,
            starting = uiState.starting,
            startingThis = uiState.startingKey == book.key(),
            isOnline = uiState.isOnline,
            onRequest = { onRequest(book) },
            onOpen = {},
            testKey = book.title,
        )
    }
}

/** A heading over its results: a spinner while they load, a line when they fail or are empty, else one row each. */
@Composable
private fun <T> Section(heading: String, results: SearchResults<T>, row: @Composable (T) -> Unit) {
    val palette = OmnibusTheme.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = heading,
            style = displayTextStyle(20, palette.ink0),
            modifier = Modifier.semantics { heading() },
        )
        when (results) {
            SearchResults.Loading -> {
                val label = stringResource(R.string.requests_add_loading)
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        color = palette.ink2,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp).semantics { contentDescription = label },
                    )
                }
            }
            SearchResults.Failed -> Message(stringResource(R.string.requests_add_failed))
            is SearchResults.Done -> if (results.items.isEmpty()) {
                Message(stringResource(R.string.requests_add_empty))
            } else {
                results.items.forEach {
                    row(it)
                    HorizontalDivider(color = palette.line2, thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, style = uiTextStyle(13.5.sp, FontWeight.Normal, OmnibusTheme.palette.ink3), modifier = Modifier.fillMaxWidth())
}

/** The line under a result's title: its parts, then what the row says about it ("requested", "in the library"). */
@Composable
private fun byline(parts: List<String?>, action: AddAction): String {
    val note = when (action) {
        AddAction.Open -> stringResource(R.string.requests_add_note_requested)
        AddAction.Held -> stringResource(R.string.requests_add_note_in_library)
        AddAction.Start -> null
    }
    return (parts + note).filter { !it.isNullOrBlank() }.joinToString(" · ")
}

/** One result: its cover, title and byline, and the control its [action] calls for, all on one row. */
@Composable
private fun ResultRow(
    cover: @Composable () -> Unit,
    title: String,
    byline: String,
    action: AddAction,
    starting: Boolean,
    startingThis: Boolean,
    isOnline: Boolean,
    onRequest: () -> Unit,
    onOpen: () -> Unit,
    testKey: String,
) {
    val palette = OmnibusTheme.palette
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(44.dp)) { cover() }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = uiTextStyle(15, FontWeight.Medium, palette.ink0),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (byline.isNotEmpty()) {
                Text(
                    text = byline,
                    style = uiTextStyle(12, FontWeight.Normal, palette.ink3),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (action) {
            AddAction.Held -> Unit
            AddAction.Open -> RequestButton(
                text = stringResource(R.string.requests_add_open),
                onClick = onOpen,
                width = RequestButtonWidth,
                enabled = !starting,
                contentDescription = stringResource(R.string.requests_add_open_title, title),
                modifier = Modifier.testTag(AddRequestTestTags.open(testKey)),
            )
            AddAction.Start -> RequestButton(
                text = stringResource(if (startingThis) R.string.requests_requesting else R.string.requests_request),
                onClick = onRequest,
                width = RequestButtonWidth,
                busy = startingThis,
                enabled = !starting && isOnline,
                contentDescription = stringResource(R.string.requests_add_request_title, title),
                modifier = Modifier.testTag(AddRequestTestTags.request(testKey)),
            )
        }
    }
}

@Composable
private fun LibraryCover(hit: LibraryHit) {
    BookCover(
        CoverIdentity(uuid = hit.uuid, title = hit.title, creator = hit.authorDisplay.takeIf { it.isNotBlank() }, accent = hit.accent, hasCover = hit.coverUrl != null),
        Modifier.fillMaxWidth(),
        cornerRadius = 4.dp,
    )
}

/** A provider's cover: the generated plate, with the provider's own art over it when it gave an http(s) address. */
@Composable
private fun ProviderCover(book: ProviderBook) {
    val url = book.coverUrl?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
    Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp))) {
        BookCover(
            CoverIdentity(uuid = book.title, title = book.title, creator = book.authors.firstOrNull(), hasCover = false),
            Modifier.fillMaxWidth(),
            cornerRadius = 4.dp,
        )
        if (url != null && !LocalInspectionMode.current) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f))
        }
    }
}
