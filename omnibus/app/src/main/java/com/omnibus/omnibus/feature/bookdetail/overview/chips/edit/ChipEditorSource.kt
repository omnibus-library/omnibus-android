package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import dagger.hilt.android.EntryPointAccessors
import com.omnibus.omnibus.data.auth.AuthRepository
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext

/** What the Genres and Tags section needs from the chip editor. Each member forwards to the editor's ViewModel. */
@Stable
internal interface ChipEditor {

    /** The editor's state. Reading it in composition recomposes when it changes. */
    val state: ChipEditUIState

    /** Seeds both lists from [book]. */
    fun bind(book: Book)

    /** Opens the sheet for [kind]. */
    fun open(kind: MetadataListKind)

    /** Closes the sheet. */
    fun close()

    /** Adds [value] to [kind]'s list. */
    fun add(kind: MetadataListKind, value: String)

    /** Removes [value] from [kind]'s list. */
    fun remove(kind: MetadataListKind, value: String)

    /** Sets the sheet's field text. */
    fun updateEntry(text: String)

    /** Acknowledges the failure [eventId], so it is reported once. */
    fun failureShown(eventId: Long)

    /** Acknowledges the book update [eventId], so it is forwarded once. */
    fun bookUpdateApplied(eventId: Long)
}

/**
 * Where a section gets its [ChipEditor]. It returns null when the user may not edit, so the section shows no "+"
 * and no sheet. Tests provide their own source through [LocalChipEditorSource], so they never reach Hilt.
 */
internal fun interface ChipEditorSource {

    /** The editor for [context]'s book, or null when the user may not edit. */
    @Composable
    fun editor(context: BookDetailSectionContext): ChipEditor?
}

/**
 * The production source. It asks the signed-in user first, and creates the ViewModel only for a user who may edit,
 * so a reader never reaches Hilt for the editor.
 */
internal object HiltChipEditorSource : ChipEditorSource {

    @Composable
    override fun editor(context: BookDetailSectionContext): ChipEditor? {
        if (LocalInspectionMode.current) return null
        val app = LocalContext.current.applicationContext
        val users = remember(app) { authRepositoryOrNull(app)?.currentUser } ?: return null
        val user by users.collectAsState(initial = null)
        if (!canEditChips(user)) return null

        val viewModel = hiltViewModel<ChipEditViewModel, ChipEditViewModel.Factory>(
            key = "chip-edit-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid) },
        )
        val state = viewModel.uiState.collectAsState()
        return remember(viewModel, state) { ViewModelChipEditor(viewModel, state) }
    }
}

/** The editor that never exists. It is for sections that must not edit: #100's tests and previews. */
internal object NoChipEditorSource : ChipEditorSource {

    @Composable
    override fun editor(context: BookDetailSectionContext): ChipEditor? = null
}

/** The active [ChipEditorSource]. Production keeps [HiltChipEditorSource]. */
internal val LocalChipEditorSource = staticCompositionLocalOf<ChipEditorSource> { HiltChipEditorSource }

/** The app's [AuthRepository], or null when the app graph is not there (a plain test host). */
private fun authRepositoryOrNull(context: Context): AuthRepository? = try {
    EntryPointAccessors.fromApplication(context, ChipEditEntryPoint::class.java).authRepository()
} catch (_: IllegalStateException) {
    null
} catch (_: ClassCastException) {
    null
}

/** Adapts [viewModel] to [ChipEditor]. [holder] is Compose state, so reading [state] recomposes. */
private class ViewModelChipEditor(
    private val viewModel: ChipEditViewModel,
    private val holder: State<ChipEditUIState>,
) : ChipEditor {
    override val state: ChipEditUIState get() = holder.value
    override fun bind(book: Book) = viewModel.bind(book)
    override fun open(kind: MetadataListKind) = viewModel.open(kind)
    override fun close() = viewModel.close()
    override fun add(kind: MetadataListKind, value: String) = viewModel.add(kind, value)
    override fun remove(kind: MetadataListKind, value: String) = viewModel.remove(kind, value)
    override fun updateEntry(text: String) = viewModel.updateEntry(text)
    override fun failureShown(eventId: Long) = viewModel.failureShown(eventId)
    override fun bookUpdateApplied(eventId: Long) = viewModel.bookUpdateApplied(eventId)
}
