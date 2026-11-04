package com.omnibus.omnibus.feature.audiobook.chapters

import com.omnibus.omnibus.data.audiobook.playback.PlaybackState
import com.omnibus.omnibus.data.audiobook.playback.chapterIndexAt
import com.omnibus.omnibus.data.audiobook.playback.spanLengthSeconds
import com.omnibus.omnibus.data.audiobook.playback.spanPositionSeconds
import com.omnibus.omnibus.feature.audiobook.PlaybackTimeFormat
import java.util.Locale

/**
 * One row of the chapters sheet (P-CHP-2).
 *
 * @property number The 1-based position in the list.
 * @property title The chapter's title as the manifest gives it.
 * @property lengthLabel The chapter's length, `m:ss` or `h:mm:ss`.
 */
data class ChapterRowModel(
    val number: Int,
    val title: String,
    val lengthLabel: String,
)

/**
 * What the chapters sheet shows.
 *
 * @property rows One per chapter, in book order.
 * @property currentIndex The index in [rows] of the chapter being played, or null with no chapters.
 * @property currentProgress How far through the current chapter the listener is, in [0, 1] (P-CHP-3).
 */
data class ChaptersUiState(
    val rows: List<ChapterRowModel> = emptyList(),
    val currentIndex: Int? = null,
    val currentProgress: Float = 0f,
)

/** Builds the sheet's state from the player's [state]. [locale] sets the digits of the lengths. */
fun chaptersUiState(state: PlaybackState, locale: Locale = Locale.getDefault()): ChaptersUiState {
    val current = chapterIndexAt(state.chapters, state.positionSeconds)
    val length = state.spanLengthSeconds
    val progress = if (current != null && length > 0) (state.spanPositionSeconds / length).toFloat() else 0f
    return ChaptersUiState(
        rows = state.chapters.mapIndexed { index, chapter ->
            ChapterRowModel(
                number = index + 1,
                title = chapter.title,
                lengthLabel = PlaybackTimeFormat.clock(chapter.durationSeconds, locale = locale),
            )
        },
        currentIndex = current,
        currentProgress = progress.coerceIn(0f, 1f),
    )
}
