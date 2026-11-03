package com.omnibus.omnibus.feature.bookdetail.highlights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnitType
import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val NOW_MILLIS = 1_793_620_800_000L

private const val OTHER_DEVICE = "A passage kept on another device, with no text to show here."
private const val EMPTY_TEXT = "Highlight while you read to keep passages here."
private const val FAILED_TEXT = "Highlights couldn't be loaded."

class HighlightsContentTest {

    @get:Rule
    val rule = createComposeRule()

    /** Renders [HighlightsContent] in dark theme inside a scrolling column, as the panel does, and returns its state. */
    private fun setContent(
        initial: HighlightsUIContent,
        onRetry: () -> Unit = {},
        darkTheme: Boolean = true,
    ) = mutableStateOf(HighlightsUIState(initial, NOW_MILLIS)).also { holder ->
        rule.setContent {
            OmnibusTheme(darkTheme = darkTheme) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    HighlightsContent(holder.value, onRetry = onRetry)
                }
            }
        }
    }

    // TC-36
    @Test
    fun tc36_loadingShowsTheHeadingOnly() {
        setContent(HighlightsUIContent.Loading)

        rule.onNodeWithTag(HighlightsTestTags.HEADING).assertIsDisplayed()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.EMPTY).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.FAILED).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.MORE).assertDoesNotExist()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(0)
    }

    // TC-37
    @Test
    fun tc37_theHeadingIsAHeadingAndTheCountFollowsTheRows() {
        val state = setContent(HighlightsUIContent.Failed)

        rule.onNodeWithTag(HighlightsTestTags.HEADING)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assertTextEquals("Highlights")
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(emptyList()), NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(listOf(testRow(1))), NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertTextEquals("1")

        state.value = HighlightsUIState(
            HighlightsUIContent.Loaded(listOf(testRow(1), testRow(2), testRow(3))),
            NOW_MILLIS,
        )
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertTextEquals("3")
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(3)
    }

    // TC-38
    @Test
    fun tc38_theCountSitsFourDpAfterTheHeadingOnTheSameLine() {
        setContent(HighlightsUIContent.Loaded(listOf(testRow(1), testRow(2), testRow(3))))

        val heading = rule.onNodeWithTag(HighlightsTestTags.HEADING).bounds()
        val count = rule.onNodeWithTag(HighlightsTestTags.COUNT).bounds()
        val gap = rule.dpOf(count.left - heading.right)
        assertTrue("count must sit 4 dp after the heading, was $gap dp", kotlin.math.abs(gap - 4f) <= 1f)
        assertTrue("count and heading share a line", count.top < heading.bottom && heading.top < count.bottom)
    }

    // TC-39
    @Test
    fun tc39_theTextStylesMatchTheDesign() {
        val longRow = testRow(1, text = "A passage with a note", note = "A note", chapterNumber = 3)
        val noText = testRow(2, text = null)
        val state = setContent(HighlightsUIContent.Loaded(listOf(longRow, noText)))

        val heading = rule.onNodeWithTag(HighlightsTestTags.HEADING).textLayout().layoutInput.style
        assertEquals(30f, heading.fontSize.value, 0.01f)
        assertEquals(FontWeight.SemiBold, heading.fontWeight)

        val count = rule.onNodeWithTag(HighlightsTestTags.COUNT).textLayout().layoutInput.style
        assertEquals(17f, count.fontSize.value, 0.01f)
        assertEquals(FontWeight.Medium, count.fontWeight)

        val passage = rule.onNodeWithTag(HighlightsTestTags.text(1), useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(22f, passage.fontSize.value, 0.01f)
        assertEquals(FontWeight.Medium, passage.fontWeight)
        assertEquals(1.34f, passage.lineHeight.value, 0.001f)
        assertEquals(TextUnitType.Em, passage.lineHeight.type)

        val note = rule.onNodeWithTag(HighlightsTestTags.note(1), useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(15f, note.fontSize.value, 0.01f)

        val other = rule.onNodeWithTag(HighlightsTestTags.noText(2), useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(15f, other.fontSize.value, 0.01f)

        val footer = rule.onNodeWithTag(HighlightsTestTags.footer(1), useUnmergedTree = true).textLayout().layoutInput.style
        assertEquals(14f, footer.fontSize.value, 0.01f)

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(emptyList()), NOW_MILLIS)
        rule.waitForIdle()
        val empty = rule.onNodeWithTag(HighlightsTestTags.EMPTY).textLayout().layoutInput.style
        assertEquals(16f, empty.fontSize.value, 0.01f)
        assertEquals(1.45f, empty.lineHeight.value, 0.001f)

        state.value = HighlightsUIState(HighlightsUIContent.Failed, NOW_MILLIS)
        rule.waitForIdle()
        val failed = rule.onNodeWithTag(HighlightsTestTags.FAILED).textLayout().layoutInput.style
        assertEquals(16f, failed.fontSize.value, 0.01f)
    }

    // TC-40
    @Test
    fun tc40_anEmptyBookShowsTheEmptyLineEightDpBelowTheHeading() {
        setContent(HighlightsUIContent.Loaded(emptyList()))

        rule.onNodeWithTag(HighlightsTestTags.EMPTY).assertTextEquals(EMPTY_TEXT)
        rule.onNodeWithTag(HighlightsTestTags.COUNT).assertDoesNotExist()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(0)
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()

        val heading = rule.onNodeWithTag(HighlightsTestTags.HEADING).bounds()
        val empty = rule.onNodeWithTag(HighlightsTestTags.EMPTY).bounds()
        val gap = rule.dpOf(empty.top - heading.bottom)
        assertTrue("empty line must sit 8 dp under the heading, was $gap dp", kotlin.math.abs(gap - 8f) <= 1f)
    }

    // TC-41
    @Test
    fun tc41_tryAgainCallsRetryOnceAndTheListComesBack() {
        var retries = 0
        val state = setContent(HighlightsUIContent.Failed, onRetry = { retries++ })

        rule.onNodeWithTag(HighlightsTestTags.FAILED).assertTextEquals(FAILED_TEXT)
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertIsDisplayed().performClick()
        rule.waitForIdle()
        assertEquals(1, retries)

        state.value = HighlightsUIState(HighlightsUIContent.Loading, NOW_MILLIS)
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.FAILED).assertDoesNotExist()
        rule.onNodeWithTag(HighlightsTestTags.RETRY).assertDoesNotExist()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(0)

        state.value = HighlightsUIState(HighlightsUIContent.Loaded(listOf(testRow(1), testRow(2))), NOW_MILLIS)
        rule.waitForIdle()
        rule.onAllNodes(highlightRowMatcher).assertCountEquals(2)
    }

    // TC-42
    @Test
    fun tc42_aRowHasItsPassageNoteFooterAndRibbonInside() {
        val row = testRow(1, text = "The passage", note = "The note", chapterNumber = 3)
        setContent(HighlightsUIContent.Loaded(listOf(row)))

        val rowBounds = rule.onNodeWithTag(HighlightsTestTags.row(1)).bounds()
        rule.onNodeWithTag(HighlightsTestTags.text(1), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(HighlightsTestTags.note(1), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(HighlightsTestTags.footer(1), useUnmergedTree = true)
            .assertIsDisplayed()
            .assertTextEquals(expectedFooter(3, row.createdAtMillis, NOW_MILLIS)!!)
        rule.onNodeWithTag(HighlightsTestTags.ribbon(1), useUnmergedTree = true).assertIsDisplayed()

        listOf(
            HighlightsTestTags.text(1),
            HighlightsTestTags.note(1),
            HighlightsTestTags.footer(1),
            HighlightsTestTags.ribbon(1),
        ).forEach { tag ->
            val child = rule.onNodeWithTag(tag, useUnmergedTree = true).bounds()
            // The ribbon hangs 1 dp above the row; one more pixel absorbs dp-to-px rounding on fractional densities.
            assertTrue("$tag must lie inside its row", inside(child, rowBounds, slopPx = rule.pxOf(1f) + 1f))
        }
    }

    // TC-43
    @Test
    fun tc43_rowVariantsShowWhatTheyHave() {
        val rows = listOf(
            testRow(1, text = "Only passage", note = null, chapterNumber = 2),
            testRow(2, text = null, note = "Kobo note", createdAtMillis = 1_761_775_445_000L, chapterNumber = null),
            testRow(3, text = "No chapter", chapterNumber = null, createdAtMillis = 1_761_775_445_000L),
            testRow(4, text = "Chapter alone", chapterNumber = 3, createdAtMillis = null),
        )
        setContent(HighlightsUIContent.Loaded(rows))

        rule.onAllNodesWithTag(HighlightsTestTags.note(1), useUnmergedTree = true).assertCountEquals(0)

        rule.onNodeWithTag(HighlightsTestTags.noText(2), useUnmergedTree = true).assertTextEquals(OTHER_DEVICE)
        rule.onAllNodesWithTag(HighlightsTestTags.text(2), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(HighlightsTestTags.note(2), useUnmergedTree = true).assertTextEquals("Kobo note")
        rule.onNodeWithTag(HighlightsTestTags.footer(2), useUnmergedTree = true)
            .assertTextEquals(expectedFooter(null, rows[1].createdAtMillis, NOW_MILLIS)!!)

        rule.onNodeWithTag(HighlightsTestTags.footer(3), useUnmergedTree = true)
            .assertTextEquals(expectedFooter(null, rows[2].createdAtMillis, NOW_MILLIS)!!)

        rule.onNodeWithTag(HighlightsTestTags.footer(4), useUnmergedTree = true).assertTextEquals(detailChapter(3))
    }

    // TC-44
    @Test
    fun tc44_theFooterRowIsAtLeast44DpWideAndTheListHasNoButtonOrClickableRow() {
        val rows = listOf(
            testRow(1, text = "No footer text", chapterNumber = null, createdAtMillis = null),
            testRow(2, text = "Normal", chapterNumber = 1),
        )
        setContent(HighlightsUIContent.Loaded(rows))

        rule.onAllNodesWithTag(HighlightsTestTags.footer(1), useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag(HighlightsTestTags.footerRow(1), useUnmergedTree = true).assertExists()
        rows.forEach { row ->
            val rowBounds = rule.onNodeWithTag(HighlightsTestTags.row(row.id)).bounds()
            val footerBounds = rule.onNodeWithTag(HighlightsTestTags.footerRow(row.id), useUnmergedTree = true).bounds()
            assertTrue(
                "footer row ${row.id} must be at least 44 dp tall, was ${rule.dpOf(footerBounds.height)} dp",
                rule.dpOf(footerBounds.height) >= 44f - 1f,
            )
            assertTrue(
                "footer row ${row.id} must span the row's width",
                kotlin.math.abs(footerBounds.left - rowBounds.left) <= rule.pxOf(1f) &&
                    kotlin.math.abs(footerBounds.right - rowBounds.right) <= rule.pxOf(1f),
            )
        }
        rule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-45
    @Test
    fun tc45_theRibbonIsThirteenByThirtyTwoDpAtTheRowsTopEnd() {
        val longPassage = "A very long passage that wraps across several lines so the row grows tall. ".repeat(6)
        setContent(HighlightsUIContent.Loaded(listOf(testRow(1, text = longPassage, chapterNumber = 2))))

        val row = rule.onNodeWithTag(HighlightsTestTags.row(1)).bounds()
        val ribbon = rule.onNodeWithTag(HighlightsTestTags.ribbon(1), useUnmergedTree = true).bounds()
        val text = rule.onNodeWithTag(HighlightsTestTags.text(1), useUnmergedTree = true).bounds()
        val footerRow = rule.onNodeWithTag(HighlightsTestTags.footerRow(1), useUnmergedTree = true).bounds()

        assertTrue("ribbon width ${rule.dpOf(ribbon.width)} dp", kotlin.math.abs(rule.dpOf(ribbon.width) - 13f) <= 1f)
        assertTrue("ribbon height ${rule.dpOf(ribbon.height)} dp", kotlin.math.abs(rule.dpOf(ribbon.height) - 32f) <= 1f)
        assertTrue("ribbon top offset", kotlin.math.abs(rule.dpOf(ribbon.top - row.top) - (-1f)) <= 1f)
        assertTrue("ribbon end inset", kotlin.math.abs(rule.dpOf(row.right - ribbon.right) - 2f) <= 1f)
        assertTrue("passage clears the ribbon", text.right <= ribbon.left)
        assertTrue("passage starts 18 dp under the top", rule.dpOf(text.top - row.top) >= 18f - 1f)
        assertTrue("footer row ends 6 dp above the bottom", kotlin.math.abs(rule.dpOf(row.bottom - footerRow.bottom) - 6f) <= 1f)
    }

    // TC-46
    @Test
    fun tc46_eachRowNamesItsColourAndMergesItsText() {
        val colours = HighlightColor.entries
        val rows = colours.mapIndexed { index, colour ->
            testRow(index + 1L, text = "Passage ${index + 1}", color = colour, chapterNumber = index + 1)
        }
        setContent(HighlightsUIContent.Loaded(rows))

        rows.forEach { row ->
            val label = when (row.color) {
                HighlightColor.Amber -> "Amber highlight"
                HighlightColor.Green -> "Green highlight"
                HighlightColor.Blue -> "Blue highlight"
                HighlightColor.Rose -> "Rose highlight"
                HighlightColor.Violet -> "Violet highlight"
            }
            rule.onNodeWithTag(HighlightsTestTags.ribbon(row.id), useUnmergedTree = true)
                .assertContentDescriptionEquals(label)
            rule.onNodeWithTag(HighlightsTestTags.row(row.id))
                .assert(hasText(requireNotNull(row.text)))
                .assert(hasText(expectedFooter(row.chapterNumber, row.createdAtMillis, NOW_MILLIS)!!))
                .assert(hasContentDescription(label))
        }
    }

    // TC-47
    @Test
    fun tc47_identicalRowsStayDistinctInBothThemesAndAtDoubleFontScale() {
        val darkTheme = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        val rows = listOf(
            testRow(1, text = "Same passage", note = "Same note", chapterNumber = 2),
            testRow(2, text = "Same passage", note = "Same note", chapterNumber = 2),
        )
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale.value)) {
                OmnibusTheme(darkTheme = darkTheme.value) {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        HighlightsContent(HighlightsUIState(HighlightsUIContent.Loaded(rows), NOW_MILLIS), onRetry = {})
                    }
                }
            }
        }

        rule.onNodeWithTag(HighlightsTestTags.row(1)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.row(2)).assertExists()
        darkTheme.value = true
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.row(1)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.row(2)).assertExists()
        rule.onNodeWithTag(HighlightsTestTags.ribbon(2), useUnmergedTree = true)
            .assertContentDescriptionEquals("Amber highlight")

        fontScale.value = 2f
        rule.waitForIdle()
        rule.onNodeWithTag(HighlightsTestTags.footer(1), useUnmergedTree = true).assertIsDisplayed()
        val row = rule.onNodeWithTag(HighlightsTestTags.row(1)).bounds()
        val footerRow = rule.onNodeWithTag(HighlightsTestTags.footerRow(1), useUnmergedTree = true).bounds()
        assertTrue(
            "footer row at double font scale must be at least 44 dp, was ${rule.dpOf(footerRow.height)} dp",
            rule.dpOf(footerRow.height) >= 44f - 1f,
        )
        listOf(
            HighlightsTestTags.text(1),
            HighlightsTestTags.note(1),
            HighlightsTestTags.footer(1),
        ).forEach { tag ->
            val child = rule.onNodeWithTag(tag, useUnmergedTree = true).bounds()
            assertTrue("$tag must lie inside its row at 2x font scale", inside(child, row, slopPx = rule.pxOf(1f)))
        }
    }
}
