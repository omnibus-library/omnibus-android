package com.omnibus.omnibus.feature.quotecard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.ui.theme.Oklch

private const val SHORT =
    "It is a truth universally acknowledged, that a single man in possession of a good fortune, " +
        "must be in want of a wife."

private const val SENTENCE = "The long road bent away from the river and the lamps came on one by one along it. "

/** More than 2,000 characters, far past what any shape can hold. */
private val LONG_PASSAGE = SENTENCE.repeat(26).trim()

private val BookTone = Oklch(0.50, 0.12, 30.0)

@Composable
private fun CardShot(
    passage: String = SHORT,
    background: QuoteBackground = QuoteBackground.Book,
    typeface: QuoteTypeface = QuoteTypeface.Serif,
    size: QuoteSize = QuoteSize.M,
    bold: Boolean = false,
    italic: Boolean = false,
    shape: QuoteShape = QuoteShape.Square,
) {
    QuoteCard(
        passage = passage,
        title = "Pride and Prejudice",
        author = "Jane Austen",
        fill = background.fill(BookTone),
        typeface = typeface,
        size = size,
        bold = bold,
        italic = italic,
        shape = shape,
        modifier = Modifier.fillMaxWidth(),
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 360)
@Composable
fun QuoteCardSquareBook() {
    CardShot()
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 450)
@Composable
fun QuoteCardPostEmber() {
    CardShot(background = QuoteBackground.Preset(QuoteGradientPreset.Ember), shape = QuoteShape.Post)
}

/** Dark ink on a pale ground. */
@PreviewTest
@Preview(widthDp = 360, heightDp = 480)
@Composable
fun QuoteCardPortraitPaper() {
    CardShot(background = QuoteBackground.Wash(QuoteWash.Paper), shape = QuoteShape.Portrait)
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 640)
@Composable
fun QuoteCardStoryLongPassage() {
    CardShot(passage = LONG_PASSAGE, shape = QuoteShape.Story)
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 360)
@Composable
fun QuoteCardSquareLongPassage() {
    CardShot(passage = LONG_PASSAGE)
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 360)
@Composable
fun QuoteCardSansBoldItalic() {
    CardShot(
        background = QuoteBackground.Wash(QuoteWash.Dusk),
        typeface = QuoteTypeface.Sans,
        bold = true,
        italic = true,
    )
}

@PreviewTest
@Preview(widthDp = 360, heightDp = 360)
@Composable
fun QuoteCardMonoLarge() {
    CardShot(background = QuoteBackground.Wash(QuoteWash.Sea), typeface = QuoteTypeface.Mono, size = QuoteSize.L)
}
