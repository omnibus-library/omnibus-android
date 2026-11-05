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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.series.SeriesMember
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The Series section as the Overview panel renders it: gating, heading, link, navigation, state and the ribbon's
 * colour. The member source is injected through [LocalSeriesSource], so no case reaches Hilt.
 */
class SeriesFanSectionTest {

    @get:Rule
    val rule = createComposeRule()

    private var sourceCalls = 0
    private var membersState by mutableStateOf<List<SeriesMember>?>(null)
    private var book by mutableStateOf(seriesBook(position = 1))

    @Composable
    private fun SectionHost(recorder: SectionContextRecorder, accent: BookAccent? = null) {
        val context: BookDetailSectionContext = if (accent == null) {
            testSectionContext(book = book, recorder = recorder)
        } else {
            testSectionContext(book = book, recorder = recorder, accent = accent)
        }
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(
                LocalSeriesSource provides SeriesSource { _, _ ->
                    sourceCalls++
                    membersState
                },
            ) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SeriesFanSection.Content(context, Modifier.fillMaxWidth())
                }
            }
        }
    }

    private fun setSection(recorder: SectionContextRecorder = SectionContextRecorder(), accent: BookAccent? = null) {
        rule.setContent { SectionHost(recorder, accent) }
        rule.waitForIdle()
    }

    // TC-92
    @Test
    fun issue104_tc92_noSeriesIdRendersNothing() {
        membersState = members(3)
        book = seriesBook(position = 1).copy(seriesId = null)
        setSection()
        rule.onAllNodesWithTag(SeriesFanTestTags.SECTION).assertCountEquals(0)
        rule.onAllNodesWithTag(SeriesFanTestTags.HEADING).assertCountEquals(0)
        assertEquals("the source is never asked", 0, sourceCalls)
    }

    // TC-93
    @Test
    fun issue104_tc93_blankSeriesNameRendersNothing() {
        membersState = members(3)
        setSection()
        for (name in listOf("", "  ", null)) {
            rule.runOnIdle { book = seriesBook(position = 1, series = name) }
            rule.waitForIdle()
            rule.onAllNodesWithTag(SeriesFanTestTags.SECTION).assertCountEquals(0)
        }
    }

    // TC-94
    @Test
    fun issue104_tc94_loadingOrSingleMemberRendersNothing() {
        setSection()
        for (state in listOf<List<SeriesMember>?>(null, emptyList(), listOf(member(1)))) {
            rule.runOnIdle { membersState = state }
            rule.waitForIdle()
            rule.onAllNodesWithTag(SeriesFanTestTags.SECTION).assertCountEquals(0)
        }
    }

    // TC-95
    @Test
    fun issue104_tc95_showsHeadingForASeries() {
        membersState = members(3)
        book = seriesBook(position = 1, series = "  Dune  ")
        setSection()
        rule.onNodeWithTag(SeriesFanTestTags.SECTION).assertIsDisplayed()
        rule.onNodeWithTag(SeriesFanTestTags.HEADING).assertTextEquals("Dune series")
        assertTrue(
            "the heading carries the heading role",
            rule.onNodeWithTag(SeriesFanTestTags.HEADING).fetchSemanticsNode().config.contains(SemanticsProperties.Heading),
        )
        rule.onAllNodesWithTag(SeriesFanTestTags.card("m2")).assertCountEquals(1)
    }

    // TC-97
    @Test
    fun issue104_tc97_seriesPageLinkAccessibility() {
        membersState = members(3)
        setSection()
        val link = rule.onNodeWithTag(SeriesFanTestTags.SERIES_PAGE_LINK)
        link.assertTextEquals("Series page")
        link.assertHeightIsAtLeast(48.dp)
        assertTrue(link.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
        assertEquals("Open the series page", link.clickLabel())
        // The chevron is drawn and adds no accessibility node: the link's only child is its text.
        rule.onAllNodes(hasAnyAncestor(hasTestTag(SeriesFanTestTags.SERIES_PAGE_LINK)), useUnmergedTree = true)
            .assertCountEquals(1)
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

    // TC-104
    @Test
    fun issue104_tc104_sectionFollowsTheMembers() {
        membersState = members(3)
        setSection()
        rule.onAllNodesWithTag(SeriesFanTestTags.card("m4")).assertCountEquals(0)

        rule.runOnIdle { membersState = members(4) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(SeriesFanTestTags.card("m4")).assertCountEquals(1)

        rule.runOnIdle { membersState = members(1) }
        rule.waitForIdle()
        rule.onAllNodesWithTag(SeriesFanTestTags.SECTION).assertCountEquals(0)
    }
}
