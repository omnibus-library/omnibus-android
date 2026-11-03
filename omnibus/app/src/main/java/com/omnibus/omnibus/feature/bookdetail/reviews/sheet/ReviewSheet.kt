package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/**
 * The review sheet window and the picker above it. A swipe, scrim tap or back on a changed draft asks to discard instead
 * of hiding. When the ViewModel closes the sheet it hides with its animation, then reports [onClosed] even if a swipe
 * has already hidden it.
 *
 * @param onRequestClose Called when the reader tries to close the sheet by a swipe, a scrim tap or back.
 * @param onClosed Called once the sheet has finished hiding after the ViewModel closed it.
 * @param onClosePicker Called when the reader dismisses the highlight picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReviewSheet(
    state: ReviewSheetUIState,
    book: Book,
    actions: ReviewSheetActions,
    onRequestClose: () -> Unit,
    onClosed: () -> Unit,
    onPick: (Long) -> Unit,
    onRetryHighlights: () -> Unit,
    onClosePicker: () -> Unit,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    val palette = OmnibusTheme.palette
    val dirty by rememberUpdatedState(state.dirty)
    val closing by rememberUpdatedState(state.closing)
    val requestClose by rememberUpdatedState(onRequestClose)
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            // A hide the ViewModel asked for is allowed; a swipe on a changed draft asks to discard instead.
            if (target != SheetValue.Hidden || closing || !dirty) {
                true
            } else {
                requestClose()
                false
            }
        },
    )
    LaunchedEffect(state.closing) {
        if (state.closing) {
            sheetState.hide()
            onClosed()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onRequestClose,
        sheetState = sheetState,
        shape = shape,
        containerColor = Color(0xFF15120F),
        contentColor = palette.ink0,
        tonalElevation = 0.dp,
        scrimColor = Color.Black.copy(alpha = 0.55f),
        dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
        contentWindowInsets = { WindowInsets(0) },
    ) {
        ReviewSheetContent(
            state = state,
            book = book,
            actions = actions,
            modifier = Modifier.statusBarsPadding().imePadding().navigationBarsPadding(),
            reducedMotion = reducedMotion,
        )
    }

    if (state.pickerOpen) {
        ModalBottomSheet(
            onDismissRequest = onClosePicker,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = shape,
            containerColor = Color(0xFF1C1814),
            contentColor = palette.ink0,
            tonalElevation = 0.dp,
            scrimColor = Color.Black.copy(alpha = 0.45f),
            dragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 5.dp, color = palette.line2) },
        ) {
            ReviewHighlightPickerContent(
                content = state.picker,
                nowMillis = state.nowMillis,
                onPick = onPick,
                onRetry = onRetryHighlights,
                onClose = onClosePicker,
            )
        }
    }
}
