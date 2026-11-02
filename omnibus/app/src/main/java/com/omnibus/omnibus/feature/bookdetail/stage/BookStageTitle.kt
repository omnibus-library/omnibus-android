package com.omnibus.omnibus.feature.bookdetail.stage

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import kotlin.math.ceil

/** The title's font size in sp: 48 up to 22 code points, 42 up to 30, 38 beyond. Untrimmed. */
internal fun bookTitleSizeSp(title: String): Int {
    val length = title.codePointCount(0, title.length)
    return when {
        length <= 22 -> 48
        length <= 30 -> 42
        else -> 38
    }
}

/** The smallest size a title shrinks to: 70% of [step], rounded up. */
internal fun titleMinSizeSp(step: Int): Int = ceil(step * 0.7).toInt()

/**
 * The book's title as the stage heading. It keeps its step when it fits in four lines, shrinks to fit,
 * then ellipsizes the fourth line.
 */
@Composable
internal fun BookStageTitle(title: String, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    val step = bookTitleSizeSp(title)
    Text(
        text = title,
        style = displayTextStyle(step.sp, palette.ink0, FontWeight.SemiBold).copy(
            textAlign = TextAlign.Center,
            letterSpacing = (-0.015).em,
            lineHeight = 0.98.em,
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
        ),
        maxLines = 4,
        overflow = TextOverflow.Ellipsis,
        autoSize = TextAutoSize.StepBased(titleMinSizeSp(step).sp, step.sp, 1.sp),
        modifier = modifier.fillMaxWidth().testTag(BookDetailTestTags.TITLE).semantics { heading() },
    )
}
