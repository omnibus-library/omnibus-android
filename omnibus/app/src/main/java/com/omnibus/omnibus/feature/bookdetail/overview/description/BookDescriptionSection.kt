package com.omnibus.omnibus.feature.bookdetail.overview.description

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.rememberReducedMotion
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The book's description, the first block of the Overview panel. Renders nothing when the book has no description. */
internal object BookDescriptionSection : BookDetailSection {
    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        BookDescription(context, modifier, rememberReducedMotion())
    }
}

/**
 * The description, clamped to [DESCRIPTION_CLAMP_LINES] lines with a Show more toggle when the full text is longer.
 * The expanded state is kept per book, and [reducedMotion] turns off the size animation.
 */
@Composable
internal fun BookDescription(context: BookDetailSectionContext, modifier: Modifier, reducedMotion: Boolean) {
    val text = remember(context.book.description) { cleanDescription(context.book.description) } ?: return
    val palette = OmnibusTheme.palette
    val style = uiTextStyle(16.sp, FontWeight.Normal, palette.ink1).copy(lineHeight = 24.sp)
    var expanded by rememberSaveable(context.book.uuid) { mutableStateOf(false) }
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.fillMaxWidth()) {
        // The toggle decision measures the unclamped text at the real width, so it holds while expanded and after a
        // width change, and never depends on [expanded].
        val fullLines = remember(text, constraints.maxWidth, style) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = constraints.maxWidth)).lineCount
        }
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (reducedMotion) Modifier else Modifier.animateContentSize(Motion.settle()))
                .testTag(BookDescriptionTestTags.DESCRIPTION),
        ) {
            Text(
                text = text,
                style = style,
                maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_CLAMP_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag(BookDescriptionTestTags.DESCRIPTION_TEXT),
            )
            if (showsDescriptionToggle(fullLines)) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clickable(role = Role.Button) { expanded = !expanded }
                        .testTag(BookDescriptionTestTags.DESCRIPTION_TOGGLE),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = stringResource(
                            if (expanded) R.string.book_detail_tabs_show_less else R.string.book_detail_tabs_show_more,
                        ),
                        style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink1),
                    )
                }
            }
        }
    }
}
