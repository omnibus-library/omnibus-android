package com.omnibus.omnibus.feature.reader

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.reader.ui.SelectionData

/** The second-row actions of the reader's menus. Enum order is row order; each entry owns when it shows, its label and its icon. */
enum class HighlightMenuAction {
    // #331 Note
    // #333 Quote card
    // #330 Share
    Copy {
        override fun isOfferedOn(target: HighlightMenuTarget): Boolean = target.hasText
        override fun labelOn(target: HighlightMenuTarget): Int = R.string.reader_selection_copy
        override val icon: Int = R.drawable.ic_reader_copy
    },
    Delete {
        override fun isOfferedOn(target: HighlightMenuTarget): Boolean = target.kind == HighlightMenuKind.Tap
        override fun labelOn(target: HighlightMenuTarget): Int = R.string.reader_highlight_delete
        override val icon: Int = R.drawable.ic_reader_delete
    };

    /** Whether this action is in the row for [target]. */
    abstract fun isOfferedOn(target: HighlightMenuTarget): Boolean

    /** The string resource for the action's TalkBack name and tooltip on [target]. */
    @StringRes
    abstract fun labelOn(target: HighlightMenuTarget): Int

    /** The action's icon. */
    @get:DrawableRes
    abstract val icon: Int
}

/** Which menu an action row belongs to: the tap menu on an existing highlight, or the menu on a new selection. */
enum class HighlightMenuKind { Tap, Create }

/**
 * What an action row is built for: the menu [kind], the target's [text] (null or blank when there is none) and whether
 * it carries a note.
 */
data class HighlightMenuTarget(val kind: HighlightMenuKind, val text: String?, val hasNote: Boolean = false) {
    /** Whether the target has text to act on, such as to copy. */
    val hasText: Boolean get() = !text.isNullOrBlank()
}

/** The actions [target] offers, in row order. */
fun highlightMenuActions(target: HighlightMenuTarget): List<HighlightMenuAction> =
    HighlightMenuAction.entries.filter { it.isOfferedOn(target) }

/** The target of the tap menu for this highlight. */
val HighlightMenuEntry.menuTarget: HighlightMenuTarget
    get() = HighlightMenuTarget(HighlightMenuKind.Tap, text, hasNote)

/** The target of the selection menu for this selection. A selection has no note. */
val SelectionData.menuTarget: HighlightMenuTarget
    get() = HighlightMenuTarget(HighlightMenuKind.Create, text, hasNote = false)
