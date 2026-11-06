package com.omnibus.omnibus.feature.bookdetail.stage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.feature.library.ui.isLightGround
import com.omnibus.omnibus.ui.theme.Oklch
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.contrastRatio

/** The colours a book's detail page takes from its cover: a tint, the ink that reads on it, and a soft wash. */
@Immutable
data class BookAccent(val tint: Color, val onTint: Color, val tintSoft: Color)

/** The dark theme's bg0, used as ink on light tints. */
internal val NearBlackInk = Color(0xFF0A0806)

/** The accent for a book with [tone] on a light or dark ground. */
internal fun bookAccent(tone: Oklch, lightGround: Boolean): BookAccent {
    val tint = Oklch(if (lightGround) 0.55 else 0.62, tone.c, tone.h).toColor()
    val tintSoft = Oklch(0.62, tone.c, tone.h, alpha = 0.16).toColor()
    val onTint = if (contrastRatio(Color.White, tint) >= contrastRatio(NearBlackInk, tint)) Color.White else NearBlackInk
    return BookAccent(tint = tint, onTint = onTint, tintSoft = tintSoft)
}

/** The accent for [book] on the current theme. */
@Composable
fun rememberBookAccent(book: Book): BookAccent {
    val light = isLightGround(OmnibusTheme.palette.bg0)
    return remember(book.accent, book.displayTitle, light) { bookAccent(book.coverIdentity().tone, light) }
}
