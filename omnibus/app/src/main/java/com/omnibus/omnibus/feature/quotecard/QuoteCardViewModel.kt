package com.omnibus.omnibus.feature.quotecard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.data.quotecard.QuoteCardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * What the quote card sheet shows: the card's choices, and [exporting] while a rendered card is being written out.
 *
 * @property coverPairs The gradients the server offers for the book's cover, darkest first.
 * @property activeStop The end of a custom gradient the colour sliders edit.
 */
data class QuoteCardUIState(
    val passage: String = "",
    val background: QuoteBackground = QuoteBackground.Book,
    val typeface: QuoteTypeface = QuoteTypeface.Serif,
    val size: QuoteSize = QuoteSize.M,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val shape: QuoteShape = QuoteShape.Square,
    val coverPairs: List<CoverPair> = emptyList(),
    val activeStop: GradientStop = GradientStop.From,
    val exporting: Boolean = false,
)

/** A one-shot request for the screen: open the share chooser or fill the clipboard with the saved card. */
sealed interface QuoteCardEffect {
    data class Share(val file: File) : QuoteCardEffect
    data class Copy(val file: File) : QuoteCardEffect
}

/**
 * Owns one open of the quote card sheet. Its state is per open, so create it keyed by the request: a reopened sheet then
 * starts fresh and never receives a previous open's [effects].
 *
 * @param bookUuid The book the passage is from.
 * @param passage The passage as stored; the card collapses its whitespace.
 */
@HiltViewModel(assistedFactory = QuoteCardViewModel.Factory::class)
class QuoteCardViewModel @AssistedInject constructor(
    @Assisted("bookUuid") bookUuid: String,
    @Assisted("passage") passage: String,
    private val repository: QuoteCardRepository,
) : ViewModel() {

    /** Creates a [QuoteCardViewModel] for a passage of the book with [bookUuid]. */
    @AssistedFactory
    interface Factory {
        fun create(@Assisted("bookUuid") bookUuid: String, @Assisted("passage") passage: String): QuoteCardViewModel
    }

    private val _uiState = MutableStateFlow(QuoteCardUIState(passage = passage))
    val uiState: StateFlow<QuoteCardUIState> = _uiState.asStateFlow()

    private val _effects = Channel<QuoteCardEffect>(Channel.BUFFERED)

    /** Each save's outcome, once: [QuoteCardEffect.Share] or [QuoteCardEffect.Copy]. A failed save emits nothing. */
    val effects: Flow<QuoteCardEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            val pairs = repository.getCoverPairs(bookUuid)
            _uiState.update { it.copy(coverPairs = pairs) }
        }
    }

    /** The reader chose a row of swatches. Applies that row's first swatch, so the card is always of the kind shown. */
    fun onKindChosen(kind: QuoteBackgroundKind) {
        _uiState.update { state ->
            if (state.background.kind == kind) state else state.copy(background = swatchesOf(kind, state.coverPairs).first())
        }
    }

    /** The reader chose a swatch. */
    fun onBackgroundChosen(background: QuoteBackground) {
        _uiState.update { it.copy(background = background) }
    }

    /** The reader chose Share with the rendered card's PNG [png]. Ignored while a save is running. */
    fun onShareRequested(png: ByteArray) = export(png, QuoteCardEffect::Share)

    /** The reader chose Copy image with the rendered card's PNG [png]. Ignored while a save is running. */
    fun onCopyRequested(png: ByteArray) = export(png, QuoteCardEffect::Copy)

    private fun export(png: ByteArray, effect: (File) -> QuoteCardEffect) {
        var accepted = false
        _uiState.update { state ->
            accepted = !state.exporting
            if (accepted) state.copy(exporting = true) else state
        }
        if (!accepted) return
        viewModelScope.launch {
            repository.saveCard(png)?.let { _effects.send(effect(it)) }
            _uiState.update { it.copy(exporting = false) }
        }
    }
}
