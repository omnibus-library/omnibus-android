package com.omnibus.omnibus.feature.audiobook.player

import androidx.compose.runtime.Composable

/** The overlays the player shows over itself. Each ticket's sheet mounts in [PlayerScreen]'s `when (overlay)`. */
enum class PlayerOverlay { Chapters, Speed, Sleep, Bookmarks, CarMode }

/**
 * The player's controls and their actions. A null action hides its control. The ticket that owns each control fills it
 * in [PlayerScreen]: chapters (#167), speed (#168), sleep (#169), car mode (#170), bookmarks and sync (#171), the book
 * button (#172).
 */
data class PlayerActions(
    /** Opens the chapter list. Null hides the chapter row. */
    val onOpenChapters: (() -> Unit)? = null,
    /** Opens the speed sheet. Null hides the speed cell. */
    val onOpenSpeed: (() -> Unit)? = null,
    /** Opens the sleep sheet. Null hides the sleep cell. */
    val onOpenSleep: (() -> Unit)? = null,
    /** The sleep pill in the top bar, shown only while a timer runs. Null hides it. */
    val sleepPill: (@Composable () -> Unit)? = null,
    /** Opens car mode. Null hides the car mode cell. */
    val onOpenCarMode: (() -> Unit)? = null,
    /** Adds a bookmark at the current position. Null hides the bookmark cell. */
    val onAddBookmark: (() -> Unit)? = null,
    /** Opens the bookmarks list from the top bar. Null hides the button. */
    val onOpenBookmarks: (() -> Unit)? = null,
    /** The sync cell, shown only for books with an ebook. Null hides it. */
    val syncHere: PlayerToolButton? = null,
    /** Opens the book's ebook from the top bar, for books with an ebook. Null hides the button. */
    val onOpenBook: (() -> Unit)? = null,
)

/** A tool-row cell whose action can be unavailable. A disabled cell is dimmed and speaks [disabledDescription]. */
data class PlayerToolButton(
    /** The cell's visible and spoken label. */
    val label: String,
    val enabled: Boolean,
    val onClick: () -> Unit,
    /** Spoken after [label] while the cell is disabled, such as why it is unavailable. */
    val disabledDescription: String? = null,
)
