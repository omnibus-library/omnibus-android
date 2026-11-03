package com.omnibus.omnibus.feature.bookdetail.more.export

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

private const val TAG = "ExportOverlay"

/**
 * Hosts the Export sheet and launches the share chooser for finished downloads (#118). The chooser starts whether or
 * not the sheet is open. Each share request is handed over once, through [onShareShown] with its event id.
 *
 * @param onShareShown Called after a share request is handled, so the ViewModel can clear it.
 * @param onShareFailed Called when no app can take the files, or they cannot be shared.
 */
@Composable
internal fun ExportOverlayContent(
    state: ExportUIState,
    context: BookDetailSectionContext,
    onSend: () -> Unit,
    onSave: (ExportSave) -> Unit,
    onClose: () -> Unit,
    onSettings: () -> Unit,
    onShareShown: (Long) -> Unit,
    onShareFailed: () -> Unit,
) {
    val androidContext = LocalContext.current
    val share = state.share
    // Keyed on the event id only, so a recomposition never opens the same chooser twice.
    LaunchedEffect(share?.eventId) {
        if (share == null) return@LaunchedEffect
        try {
            androidContext.startActivity(exportChooserIntent(androidContext, share.files))
        } catch (ex: ActivityNotFoundException) {
            Log.w(TAG, "No app can receive the exported files", ex)
            onShareFailed()
        } catch (ex: IllegalArgumentException) {
            Log.w(TAG, "The exported files are outside the shared folder", ex)
            onShareFailed()
        }
        onShareShown(share.eventId)
    }
    if (state.sheetOpen) {
        val accentInk = remember(context.book) { exportAccentInk(context.book) }
        ExportSheet(
            model = exportSheet(context.book, state.user, state.isOnline, state.kindle, state.saving),
            saveError = state.saveError,
            sending = state.kindle == KindlePhase.Sending,
            accentInk = accentInk,
            onSend = onSend,
            onSave = onSave,
            onSettings = {
                onSettings()
                context.onNavigate(BookDetailDestination.Settings)
            },
            onOpen = { context.onNavigate(BookDetailDestination.ExternalUrl(KINDLE_WEB_UPLOAD_URL)) },
            onClose = onClose,
        )
    }
}
