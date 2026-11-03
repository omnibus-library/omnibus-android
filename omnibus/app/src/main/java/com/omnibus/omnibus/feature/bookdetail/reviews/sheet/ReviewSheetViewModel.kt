package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.bookprogress.BookChaptersResult
import com.omnibus.omnibus.data.bookprogress.BookProgressRepository
import com.omnibus.omnibus.data.highlights.HighlightsRepository
import com.omnibus.omnibus.data.highlights.HighlightsState
import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.ReviewError
import com.omnibus.omnibus.data.reviews.ReviewWriteResult
import com.omnibus.omnibus.data.reviews.ReviewsRepository
import com.omnibus.omnibus.data.reviews.ReviewsState
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRowModel
import com.omnibus.omnibus.feature.bookdetail.progress.BookChapter
import com.omnibus.omnibus.feature.bookdetail.reviews.mine.ReviewSavePulse
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A save that did not reach the server. [offline] is true when the device had no connection, so the sheet says so. */
data class ReviewSheetFailure(val offline: Boolean)

/** The highlight picker's list: still loading, failed, or the rows to choose from (possibly none). */
sealed interface PickerContent {
    /** No answer yet. */
    data object Loading : PickerContent

    /** The highlights could not be loaded. */
    data object Failed : PickerContent

    /** The highlights that have text, ready to quote. */
    data class Loaded(val rows: List<HighlightRowModel>) : PickerContent
}

/**
 * What the review sheet shows. [open] is false when the sheet is closed, and then the other fields are defaults. [closing]
 * means the sheet is hiding, and [closed] should follow. [shown] is what each field displays: the draft where touched,
 * the latest server value elsewhere.
 */
data class ReviewSheetUIState(
    val open: Boolean = false,
    val closing: Boolean = false,
    val title: ReviewSheetTitle = ReviewSheetTitle.Write,
    val shown: ReviewFields = ReviewFields.Empty,
    val dirty: Boolean = false,
    val saveEnabled: Boolean = false,
    val saving: Boolean = false,
    val conflicts: List<ReviewField> = emptyList(),
    val notesExpanded: Boolean = false,
    val confirmingDiscard: Boolean = false,
    val pickerOpen: Boolean = false,
    val picker: PickerContent = PickerContent.Loading,
    val failure: ReviewSheetFailure? = null,
    val nowMillis: Long = 0L,
)

/** The open sheet's own state, kept in the ViewModel so it survives rotation. [opened] is the review as the sheet opened. */
private data class Session(
    val opened: ReviewFields,
    val draft: ReviewDraft = ReviewDraft(),
    val notesExpanded: Boolean,
    val pickerOpen: Boolean = false,
    val confirmingDiscard: Boolean = false,
    val saving: Boolean = false,
    val closing: Boolean = false,
    val failure: ReviewSheetFailure? = null,
)

/**
 * Owns the review sheet for one book: the reader's draft, the picker, the discard confirmation and the save. The
 * reviews come from the shared [ReviewsRepository] flow, so the sheet sees the card's and the stage's updates and any
 * change made elsewhere.
 *
 * Text edits update the state synchronously, so a text field bound to [uiState] keeps its cursor.
 *
 * @property bookUuid The book whose review the sheet edits.
 */
@HiltViewModel(assistedFactory = ReviewSheetViewModel.Factory::class)
class ReviewSheetViewModel @AssistedInject constructor(
    @Assisted private val bookUuid: String,
    private val reviewsRepository: ReviewsRepository,
    private val highlightsRepository: HighlightsRepository,
    private val progressRepository: BookProgressRepository,
    private val reviewSavePulse: ReviewSavePulse,
    private val timeSource: TimeSource,
) : ViewModel() {

    /** Creates a [ReviewSheetViewModel] for the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(bookUuid: String): ReviewSheetViewModel
    }

    /** The shared reviews, kept active while the sheet is open so the conflict note sees the server's changes. */
    private val reviews: StateFlow<ReviewsState> =
        reviewsRepository.getReviewsFlow(bookUuid).stateIn(viewModelScope, SharingStarted.Eagerly, ReviewsState.Loading)

    private val session = MutableStateFlow<Session?>(null)
    private val chapters = MutableStateFlow<List<BookChapter>?>(null)
    private var chaptersJob: Job? = null

    /** The highlights, collected only while the picker is open. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val highlights: Flow<HighlightsState?> = session.map { it?.pickerOpen == true }.distinctUntilChanged()
        .flatMapLatest { open -> if (open) highlightsRepository.getHighlightsFlow(bookUuid) else flowOf(null) }

    /** The sheet's state. Eager, so [uiState] is current even with no collector. */
    val uiState: StateFlow<ReviewSheetUIState> = combine(reviews, session, highlights, chapters) { r, s, h, ch ->
        if (s == null) return@combine ReviewSheetUIState()
        val server = ReviewFields.of((r as? ReviewsState.Loaded)?.reviews?.mine)
        val dirty = s.draft.isDirty(s.opened)
        ReviewSheetUIState(
            open = true,
            closing = s.closing,
            title = titleFor(s.opened),
            shown = s.draft.displayed(server),
            dirty = dirty,
            saveEnabled = dirty && !s.saving && !s.closing,
            saving = s.saving,
            conflicts = s.draft.conflicts(s.opened, server),
            notesExpanded = s.notesExpanded,
            confirmingDiscard = s.confirmingDiscard,
            pickerOpen = s.pickerOpen,
            picker = when (h) {
                is HighlightsState.Loaded -> PickerContent.Loaded(highlightPickerRows(h.highlights, ch.orEmpty()))
                HighlightsState.Failed -> PickerContent.Failed
                else -> PickerContent.Loading
            },
            failure = s.failure,
            nowMillis = timeSource.nowMillis(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ReviewSheetUIState())

    /**
     * Opens the sheet on the reader's review, with the draft empty. Does nothing unless the reviews are loaded, and does
     * nothing while the sheet is already open, so a reopen keeps the draft.
     */
    fun open() {
        val loaded = reviews.value as? ReviewsState.Loaded ?: return
        if (session.value != null) return
        val opened = ReviewFields.of(loaded.reviews.mine)
        session.value = Session(opened = opened, notesExpanded = opened.notes != null)
    }

    /** Sets the draft's rating, or clears it for null. */
    fun setRating(value: Float?) = edit { it.copy(draft = it.draft.copy(rating = Patch.Set(value)), failure = null) }

    /** Sets the draft's review text, as typed. */
    fun setText(value: String) = edit { it.copy(draft = it.draft.copy(text = Patch.Set(value)), failure = null) }

    /** Sets the draft's private notes, as typed. */
    fun setNotes(value: String) = edit { it.copy(draft = it.draft.copy(notes = Patch.Set(value)), failure = null) }

    /** Sets the draft's spoiler flag. */
    fun setSpoiler(value: Boolean) = edit { it.copy(draft = it.draft.copy(spoiler = Patch.Set(value)), failure = null) }

    /** Shows the private notes field. */
    fun expandNotes() = edit { it.copy(notesExpanded = true) }

    /**
     * Shows the highlight picker. The chapters are fetched once, on the first open; a failure counts as no chapters, so a
     * later open does not fetch them again.
     */
    fun openPicker() {
        val current = session.value ?: return
        if (current.closing) return
        edit { it.copy(pickerOpen = true) }
        if (chapters.value == null && chaptersJob?.isActive != true) {
            chaptersJob = viewModelScope.launch {
                chapters.value = (progressRepository.getChapters(bookUuid) as? BookChaptersResult.Success)
                    ?.ebookChapters ?: emptyList()
            }
        }
    }

    /** Hides the highlight picker. */
    fun closePicker() = edit { it.copy(pickerOpen = false) }

    /** Fetches the highlights again, after the picker failed to load them. */
    fun retryHighlights() {
        viewModelScope.launch { highlightsRepository.refresh(bookUuid) }
    }

    /**
     * Quotes the highlight [id] at the start of the review text, then closes the picker. Does nothing for an id that is
     * not in the loaded picker, or a highlight with no text.
     */
    fun pickHighlight(id: Long) {
        val row = (uiState.value.picker as? PickerContent.Loaded)?.rows?.firstOrNull { it.id == id } ?: return
        val text = row.text ?: return
        setText(quoteIntoReview(text, uiState.value.shown.text))
        closePicker()
    }

    /**
     * The reader asked to close the sheet, by Cancel, the scrim, back or a swipe. A draft with changes asks to discard
     * them first; a clean one closes at once. Nothing happens while a save is in flight.
     */
    fun requestClose() {
        val current = session.value ?: return
        if (current.closing || current.saving) return
        if (current.draft.isDirty(current.opened)) {
            edit { it.copy(confirmingDiscard = true) }
        } else {
            edit { it.copy(closing = true) }
        }
    }

    /** Dismisses the discard confirmation and keeps the draft. */
    fun keepEditing() = edit { it.copy(confirmingDiscard = false) }

    /** Drops the draft and closes the sheet. Nothing is written. */
    fun discard() = edit { it.copy(confirmingDiscard = false, closing = true) }

    /** Ends the session once the sheet has finished hiding. */
    fun closed() {
        session.value = null
    }

    /**
     * Saves the fields the reader changed. Does nothing unless Save is enabled. A saved review closes the sheet, and a
     * saved rating bumps the stage's pulse. A failed save keeps the draft and shows the failure.
     */
    fun save() {
        val current = session.value ?: return
        if (current.saving || current.closing || !current.draft.isDirty(current.opened)) return
        val server = ReviewFields.of((reviews.value as? ReviewsState.Loaded)?.reviews?.mine)
        val patch = current.draft.savePatch(server)
        session.update { it?.copy(saving = true, failure = null) }
        viewModelScope.launch {
            when (val result = reviewsRepository.updateMyReview(bookUuid, patch)) {
                is ReviewWriteResult.Saved -> {
                    if (patch.rating is Patch.Set) reviewSavePulse.bump(bookUuid)
                    session.update { it?.copy(saving = false, closing = true) }
                }
                ReviewWriteResult.Unchanged -> session.update { it?.copy(saving = false, closing = true) }
                is ReviewWriteResult.Failed -> session.update {
                    it?.copy(
                        saving = false,
                        failure = ReviewSheetFailure(
                            offline = result.error == ReviewError.NoServer || result.error == ReviewError.Unreachable,
                        ),
                    )
                }
            }
        }
    }

    /** Applies [change] to the open session, unless the sheet is closed or already closing. */
    private fun edit(change: (Session) -> Session) {
        session.update { current -> if (current == null || current.closing) current else change(current) }
    }
}
