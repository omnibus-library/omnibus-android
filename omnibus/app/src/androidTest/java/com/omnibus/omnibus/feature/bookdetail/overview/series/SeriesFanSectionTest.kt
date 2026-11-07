package com.omnibus.omnibus.feature.bookdetail.overview.series

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Series section as the Overview panel renders it: heading, link, navigation and state. The member source is
 * injected through [LocalSeriesSource], so no case reaches Hilt.
 */
class SeriesFanSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private var membersState by mutableStateOf<List<SeriesMember>?>(null)
    private var book by mutableStateOf(seriesBook(position = 1))

    @Composable
    private fun SectionHost(recorder: SectionContextRecorder) {
        val context = testSectionContext(book = book, recorder = recorder)
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalSeriesSource provides SeriesSource { _, _ -> membersState }) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SeriesFanSection.Content(context, Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun setSection(recorder: SectionContextRecorder = SectionContextRecorder()) {
        rule.setContent { SectionHost(recorder) }
        rule.waitForIdle()
    }

    // TC-95
    @Test
    fun issue104_tc95_theHeadingIsAHeading() {
        membersState = members(3)
        setSection()
        assertTrue(
            "the heading carries the heading role",
            rule.onNodeWithTag(SeriesFanTestTags.HEADING).fetchSemanticsNode().config.contains(SemanticsProperties.Heading),
        )
    }

    // TC-98
    @Test
    fun issue104_tc98_linkOpensTheSeriesPage() {
        membersState = members(3)
        book = seriesBook(position = 1, series = "Dune")
        val recorder = SectionContextRecorder()
        setSection(recorder)
        rule.onNodeWithTag(SeriesFanTestTags.SERIES_PAGE_LINK).performClick()
        rule.waitForIdle()
        assertEquals(listOf<BookDetailDestination>(BookDetailDestination.Series(4L, "Dune")), recorder.navigations)
    }

    // TC-99
    @Test
    fun issue104_tc99_spreadOpenAndGatherThroughTheSection() {
        membersState = members(3)
        val recorder = SectionContextRecorder()
        setSection(recorder)
        rule.onNodeWithTag(SeriesFanTestTags.card("m1")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SeriesFanTestTags.card("m3")).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(SeriesFanTestTags.card("m2")).performClick()
        rule.waitForIdle()
        assertEquals(listOf<BookDetailDestination>(BookDetailDestination.Book("m3")), recorder.navigations)
        rule.onNodeWithTag(SeriesFanTestTags.card("m2")).assertIsDisplayed()
        assertEquals(
            "gathered again: the other covers read Show the whole series",
            "Show the whole series",
            rule.onNodeWithTag(SeriesFanTestTags.card("m1")).clickLabel(),
        )
    }

    // TC-100
    @Test
    fun issue104_tc100_spreadStateSurvivesRecreation() {
        membersState = members(3)
        val recorder = SectionContextRecorder()
        val tester = StateRestorationTester(rule)
        tester.setContent { SectionHost(recorder) }
        rule.waitForIdle()
        rule.onNodeWithTag(SeriesFanTestTags.card("m1")).performClick()
        rule.waitForIdle()
        assertEquals("Open book", rule.onNodeWithTag(SeriesFanTestTags.card("m1")).clickLabel())

        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()
        assertEquals("still spread after recreation", "Open book", rule.onNodeWithTag(SeriesFanTestTags.card("m1")).clickLabel())
    }

    // TC-101
    @Test
    fun issue104_tc101_spreadStateIsPerBook() {
        membersState = members(3)
        setSection()
        val first = seriesBook(position = 1)
        val second = seriesBook(position = 2)
        rule.onNodeWithTag(SeriesFanTestTags.card("m1")).performClick()
        rule.waitForIdle()

        rule.runOnIdle { book = second }
        rule.waitForIdle()
        assertEquals("the other book starts gathered", "Show the whole series", rule.onNodeWithTag(SeriesFanTestTags.card("m1")).clickLabel())

        rule.runOnIdle { book = first }
        rule.waitForIdle()
        assertEquals("switching back is gathered too", "Show the whole series", rule.onNodeWithTag(SeriesFanTestTags.card("m1")).clickLabel())
    }
}
