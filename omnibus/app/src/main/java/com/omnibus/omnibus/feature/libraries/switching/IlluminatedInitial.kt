package com.omnibus.omnibus.feature.libraries.switching

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.libraries.LibraryMonogram
import com.omnibus.omnibus.feature.libraries.Monogram
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.text.Normalizer

/** Test tag of the framed initial. Tests only: the initial is decorative for TalkBack. */
internal const val ILLUMINATED_INITIAL_TAG = "illuminated-initial"

/**
 * The library's monogram drawn large in an ornamental frame, the first letter of its name on the switching screen (#369).
 * Decorative: it has no semantics of its own.
 */
@Composable
fun IlluminatedInitial(
    monogram: Monogram,
    modifier: Modifier = Modifier,
    size: Dp = BookcaseGeometry.INITIAL_SIZE,
) {
    val palette = OmnibusTheme.palette
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { this[SemanticsProperties.TestTag] = ILLUMINATED_INITIAL_TAG },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_illuminated_frame),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
        CompositionLocalProvider(LocalContentColor provides palette.ink0) {
            // A glyph about half the frame, as in the spec render.
            LibraryMonogram(monogram, size * 0.85f)
        }
    }
}

/**
 * The name with its first letter taken out when the illuminated initial stands for it: "Home" with H as the initial
 * reads "ome". A name that does not start with the monogram's letter, or a Maltese cross, keeps its full name.
 */
internal fun nameAfterInitial(name: String, monogram: Monogram): String {
    if (monogram !is Monogram.Letter) return name
    val normalized = Normalizer.normalize(name, Normalizer.Form.NFC)
    if (normalized.isEmpty()) return name
    val first = normalized.codePointAt(0)
    val firstLetter = String(Character.toChars(Character.toUpperCase(first)))
    return if (firstLetter == monogram.letter) normalized.substring(Character.charCount(first)) else name
}
