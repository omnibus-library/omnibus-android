package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuEntry
import com.omnibus.omnibus.feature.bookdetail.more.BookMoreMenuRow
import com.omnibus.omnibus.feature.bookdetail.more.LocalMoreMenuActivated

/** The More menu's export entry (#118): its row, and the Export sheet it opens. */
internal object ExportMenuEntry : BookMoreMenuEntry {

    @Composable
    override fun Rows(context: BookDetailSectionContext, dismissMenu: () -> Unit) {
        val viewModel = hiltViewModel<ExportViewModel, ExportViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        if (!showsExportRow(context.book, state.user)) return
        ExportMenuRow {
            dismissMenu()
            viewModel.openSheet()
        }
    }

    @Composable
    override fun Overlay(context: BookDetailSectionContext) {
        if (!LocalMoreMenuActivated.current) return
        val viewModel = hiltViewModel<ExportViewModel, ExportViewModel.Factory>(
            creationCallback = { it.create(context.book.uuid) },
        )
        val state by viewModel.uiState.collectAsState()
        ExportOverlayContent(
            state = state,
            context = context,
            onSend = viewModel::sendToKindle,
            onSave = { viewModel.save(it, context.book) },
            onClose = viewModel::closeSheet,
            onSettings = viewModel::kindleSettingsTapped,
            onShareShown = viewModel::shareShown,
            onShareFailed = viewModel::shareFailed,
        )
    }
}

/** The Export row of the More menu. */
@Composable
internal fun ExportMenuRow(onClick: () -> Unit) {
    BookMoreMenuRow(
        label = stringResource(R.string.book_detail_export_menu),
        icon = R.drawable.ic_book_detail_more_export,
        enabled = true,
        subLabel = null,
        onClick = onClick,
        modifier = Modifier.testTag(ExportTestTags.MENU_ROW),
    )
}
