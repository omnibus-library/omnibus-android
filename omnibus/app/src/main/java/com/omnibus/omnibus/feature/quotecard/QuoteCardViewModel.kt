package com.omnibus.omnibus.feature.quotecard

import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omnibus.omnibus.data.quotecard.CoverPair
import com.omnibus.omnibus.data.quotecard.QuoteCardRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
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
    /** Open the share chooser for the saved card [file]. */
    data class Share(val file: File) : QuoteCardEffect
    /** Put the saved card [file] on the clipboard. */
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
        /** A ViewModel for one open of the sheet. */
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

    /**
     * The reader chose Custom on the current row. A custom colour is seeded from what the card shows now, [current],
     * so the card doesn't change until the reader edits it. Choosing Custom again keeps the colour being edited.
     */
    fun onCustomChosen(current: QuoteFill) {
        _uiState.update { state ->
            val seeded = when (state.background) {
                is QuoteBackground.CustomSolid, is QuoteBackground.CustomGradient -> state.background
                else -> when (state.background.kind) {
                    QuoteBackgroundKind.Solid -> QuoteBackground.CustomSolid(current.from.toArgb())
                    QuoteBackgroundKind.Gradient ->
                        QuoteBackground.CustomGradient(current.from.toArgb(), current.to.toArgb(), current.angle)
                }
            }
            state.copy(background = seeded, activeStop = GradientStop.From)
        }
    }

    /** The reader moved a colour slider. Sets the custom solid, or the active stop of the custom gradient. */
    fun onCustomColorChanged(argb: Int) {
        _uiState.update { state ->
            val background = state.background
            when {
                background is QuoteBackground.CustomSolid -> state.copy(background = background.copy(argb = argb))
                background is QuoteBackground.CustomGradient && state.activeStop == GradientStop.From ->
                    state.copy(background = background.copy(fromArgb = argb))
                background is QuoteBackground.CustomGradient -> state.copy(background = background.copy(toArgb = argb))
                else -> state
            }
        }
    }

    /** The reader chose which end of the custom gradient the sliders edit. */
    fun onStopFocused(stop: GradientStop) = _uiState.update { it.copy(activeStop = stop) }

    /** The reader turned the custom gradient's direction a step: 45 degrees clockwise. */
    fun onDirectionRotated() {
        _uiState.update { state ->
            val background = state.background
            if (background is QuoteBackground.CustomGradient) {
                state.copy(background = background.copy(angle = (background.angle + 45) % 360))
            } else {
                state
            }
        }
    }

    /** The reader chose a typeface. */
    fun onTypefaceChosen(typeface: QuoteTypeface) = _uiState.update { it.copy(typeface = typeface) }

    /** The reader chose a size. */
    fun onSizeChosen(size: QuoteSize) = _uiState.update { it.copy(size = size) }

    /** The reader toggled Bold. */
    fun onBoldToggled() = _uiState.update { it.copy(bold = !it.bold) }

    /** The reader toggled Italic. */
    fun onItalicToggled() = _uiState.update { it.copy(italic = !it.italic) }

    /** The reader chose a shape. */
    fun onShapeChosen(shape: QuoteShape) = _uiState.update { it.copy(shape = shape) }

    /**
     * The reader chose Share. [capture] renders the card as PNG bytes. The guard is set before it runs, so a second
     * request is ignored from the first tap on, and a capture that throws reads as a failed save.
     */
    fun onShareRequested(capture: suspend () -> ByteArray) = export(capture, QuoteCardEffect::Share)

    /** The reader chose Copy image. Guarded and failing as [onShareRequested] does. */
    fun onCopyRequested(capture: suspend () -> ByteArray) = export(capture, QuoteCardEffect::Copy)

    private fun export(capture: suspend () -> ByteArray, effect: (File) -> QuoteCardEffect) {
        var accepted = false
        _uiState.update { state ->
            accepted = !state.exporting
            if (accepted) state.copy(exporting = true) else state
        }
        if (!accepted) return
        viewModelScope.launch {
            val saved = try {
                repository.saveCard(capture())
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                null
            }
            saved?.let { _effects.send(effect(it)) }
            _uiState.update { it.copy(exporting = false) }
        }
    }
}
