package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.Oklch

/**
 * The action bar's colours: [base] is the pill, [fill] is the lighter colour drawn over it to show progress, and
 * [content] is the label and icon. The label stays white on the deep base in both themes.
 */
@Immutable
data class ActionBarColors(val base: Color, val fill: Color, val content: Color)

/** The colours for a cover [tone]: the deep base at lightness 0.44 and the fill at 0.52. */
internal fun actionBarColors(tone: Oklch): ActionBarColors = ActionBarColors(
    base = Oklch(0.44, tone.c, tone.h).toColor(),
    fill = Oklch(0.52, tone.c, tone.h).toColor(),
    content = Color.White,
)

/** The colours for [book], from its cover tone. */
internal fun actionBarColors(book: Book): ActionBarColors = actionBarColors(book.coverIdentity().tone)
