package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * The quote card sheet for [passage] of [book]: a live preview, the style controls, and Share or Copy image. Open it from
 * anywhere a passage can be picked. Its state is per open, so closing it and opening it again starts from the defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuoteCardSheet(passage: String, book: CoverIdentity, onDismiss: () -> Unit) {
    val requestId = rememberSaveable { UUID.randomUUID().toString() }
    val viewModel = hiltViewModel<QuoteCardViewModel, QuoteCardViewModel.Factory>(
        key = "quote-card-$requestId",
        creationCallback = { it.create(book.uuid, passage) },
    )
    val state by viewModel.uiState.collectAsState()
    val exporter = rememberQuoteCardExporter()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is QuoteCardEffect.Share -> context.startActivity(quoteCardShareIntent(context, effect.file))
                is QuoteCardEffect.Copy -> clipboard.setClipEntry(ClipEntry(quoteCardClip(context, effect.file)))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier
            .shadow(elevation = 8.dp, shape = shape, clip = false)
            .border(0.5.dp, palette.line, shape),
        shape = shape,
        containerColor = palette.bg0,
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = palette.line2) },
    ) {
        QuoteCardSheetContent(
            state = state,
            book = book,
            onKindChosen = viewModel::onKindChosen,
            onBackgroundChosen = viewModel::onBackgroundChosen,
            onCustomChosen = {},
            onTypefaceChosen = viewModel::onTypefaceChosen,
            onSizeChosen = viewModel::onSizeChosen,
            onBoldToggled = viewModel::onBoldToggled,
            onItalicToggled = viewModel::onItalicToggled,
            onShapeChosen = viewModel::onShapeChosen,
            onShare = { scope.launch { viewModel.onShareRequested(exporter.capture()) } },
            onCopy = { scope.launch { viewModel.onCopyRequested(exporter.capture()) } },
            onDone = {
                scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onDismiss() }
            },
        )
        exporter.Offscreen(state.shape) { modifier -> QuoteCardFor(state, book, modifier) }
    }
}

/** The card for [state] and [book], in the book's colour when the background is Book. */
@Composable
internal fun QuoteCardFor(state: QuoteCardUIState, book: CoverIdentity, modifier: Modifier = Modifier) {
    QuoteCard(
        passage = state.passage,
        title = book.title,
        author = book.creator,
        fill = state.background.fill(book.tone),
        typeface = state.typeface,
        size = state.size,
        bold = state.bold,
        italic = state.italic,
        shape = state.shape,
        modifier = modifier,
    )
}

/** The sheet's body, without the sheet or its ViewModel, so it can be previewed. */
@Composable
internal fun QuoteCardSheetContent(
    state: QuoteCardUIState,
    book: CoverIdentity,
    onKindChosen: (QuoteBackgroundKind) -> Unit,
    onBackgroundChosen: (QuoteBackground) -> Unit,
    onCustomChosen: () -> Unit,
    onTypefaceChosen: (QuoteTypeface) -> Unit,
    onSizeChosen: (QuoteSize) -> Unit,
    onBoldToggled: () -> Unit,
    onItalicToggled: () -> Unit,
    onShapeChosen: (QuoteShape) -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = OmnibusTheme.palette
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(QuoteCardTestTags.SHEET)
            .background(palette.bg0)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Header(onDone)
        val cardShape = RoundedCornerShape(Radius.lg)
        QuoteCardFor(
            state, book,
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, cardShape, clip = true, ambientColor = Color.Black.copy(alpha = 0.28f), spotColor = Color.Black.copy(alpha = 0.28f))
                .testTag(QuoteCardTestTags.PREVIEW),
        )
        QuoteBackgroundSection(
            background = state.background,
            coverPairs = state.coverPairs,
            book = book,
            onKindChosen = onKindChosen,
            onBackgroundChosen = onBackgroundChosen,
            onCustomChosen = onCustomChosen,
        )
        QuoteTypeSection(state, onTypefaceChosen, onSizeChosen, onBoldToggled, onItalicToggled)
        QuoteShapeSection(state.shape, onShapeChosen)
        Actions(state.exporting, onShare, onCopy)
    }
}

@Composable
private fun Header(onDone: () -> Unit) {
    val palette = OmnibusTheme.palette
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.quote_card_title),
            modifier = Modifier.weight(1f),
            style = displayTextStyle(28, palette.ink0),
        )
        Box(
            Modifier
                .testTag(QuoteCardTestTags.DONE)
                .heightIn(min = 48.dp)
                .widthIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onDone),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.quote_card_done),
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
            )
        }
    }
}

@Composable
private fun Actions(exporting: Boolean, onShare: () -> Unit, onCopy: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        FilledButton(
            text = stringResource(R.string.quote_card_share),
            onClick = onShare,
            modifier = Modifier.testTag(QuoteCardTestTags.SHARE),
            enabled = !exporting,
        )
        FilledButton(
            text = stringResource(R.string.quote_card_copy),
            onClick = onCopy,
            modifier = Modifier.testTag(QuoteCardTestTags.COPY),
            enabled = !exporting,
            prominent = false,
        )
    }
}
