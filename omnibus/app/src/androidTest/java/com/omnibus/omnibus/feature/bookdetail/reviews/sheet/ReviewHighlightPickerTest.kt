package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightRowModel
import com.omnibus.omnibus.feature.bookdetail.highlights.HighlightsTestTags
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The "From your highlights" picker: its heading, retry, and picking a row. ReviewHighlightPickerScreenshotTest covers
 * how each state looks.
 */
class ReviewHighlightPickerTest {

    @get:Rule
    val rule = createComposeRule()

    /** A fixed time for the rows' dates, so no test depends on the clock. */
    private val nowMillis = 1_700_000_000_000L

    /** Shows the picker for the content in [content], so a test can change it between steps. */
    private fun show(
        content: MutableState<PickerContent>,
        onPick: (Long) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ReviewHighlightPickerContent(
                        content = content.value,
                        nowMillis = nowMillis,
                        onPick = onPick,
                        onRetry = onRetry,
                        onClose = {},
                    )
                }
            }
        }
    }

    /** A row with the given passage and chapter, with no date, so its footer is only the chapter. */
    private fun row(id: Long, text: String, chapter: Int?) = HighlightRowModel(
        id = id,
        text = text,
        note = null,
        color = HighlightColor.Amber,
        chapterNumber = chapter,
        createdAtMillis = null,
    )

    @Test
    fun tc97_theTitleIsAHeading() {
        show(mutableStateOf(PickerContent.Loading))

        rule.onNodeWithTag(ReviewSheetTestTags.PICKER_TITLE)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
    }

    @Test
    fun tc98_retryCallsBackAndLoadedRowsAreListed() {
        var retries = 0
        val content = mutableStateOf<PickerContent>(PickerContent.Failed)
        show(content, onRetry = { retries++ })

        rule.onNodeWithText("Retry").performClick()
        assertEquals(1, retries)

        content.value = PickerContent.Loaded(listOf(row(1L, "First", null), row(2L, "Second", 3)))
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.row(1L)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.row(2L)).assertExists()
    }

    @Test
    fun tc100_tappingARowPicksItsIdAndTheRowIsAButtonAtLeast48dpTall() {
        var picked = -1L
        show(
            mutableStateOf(PickerContent.Loaded(listOf(row(1L, "One", null), row(2L, "Two", null)))),
            onPick = { picked = it },
        )

        val second = rule.onNodeWithTag(HighlightsTestTags.row(2L))
        second.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        second.assert(hasClickAction())
        second.assertHeightIsAtLeast(48.dp)
        second.performClick()

        assertEquals(2L, picked)
    }
}
