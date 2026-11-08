package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.feature.library.selectionAfterRefresh
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Paging, selection, taps and semantics. ContinueCarouselScreenshotTest covers how the cards and dots look. */
class ContinueCarouselTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue22_tc27_dotsAreHiddenFromAccessibility() {
        setCarousel(listOf(card(1L), card(2L), card(3L)), selected = "b1:epub")

        rule.onNodeWithTag(ContinueTestTags.DOTS, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        rule.onNodeWithTag(ContinueTestTags.dot(0), useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test
    fun issue22_tc28_selectionFollowsTheSettledPageOnly() {
        val settled = mutableListOf<String>()
        setCarousel(
            listOf(card(1L), card(2L), card(3L)),
            selected = "b1:epub",
            onSettled = { settled += it },
        )

        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.6f, 0f))
        }
        rule.waitForIdle()

        assertTrue("nothing settles while the finger is down", settled.all { it == "b1:epub" })

        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals("b2:epub", settled.last())
    }

    @Test
    fun issue22_tc29_visibleCardSurvivesAListChangeAndExternalSelectionScrolls() {
        val settled = mutableListOf<String>()
        val cards = mutableStateOf(listOf(card(1L), card(2L)))
        val selected = mutableStateOf("b2:epub")
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val pageState = rememberContinueCarouselState(cardCount = { cards.value.size })
                    ContinueCarousel(
                        cards = cards.value,
                        selectedId = selected.value,
                        state = pageState,
                        onSettled = {
                            settled += it
                            selected.value = it
                        },
                        onOpenBook = {},
                        onResume = {},
                        onShowContextMenu = {},
                        cardsPerPage = { _, _ -> 1 },
                    )
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).assertIsDisplayed()

        rule.runOnIdle { cards.value = listOf(card(3L), card(1L), card(2L)) }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).assertIsDisplayed()
        assertFalse("A and C are never reported", settled.any { it == "b1:epub" || it == "b3:epub" })

        rule.runOnIdle { selected.value = "b3:epub" }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun issue22_tc31_tapsRouteToTheRightCallbackAndGiveHaptics() {
        val haptics = CountingHaptics()
        val opened = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val menus = mutableListOf<String>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalHapticFeedback provides haptics) {
                    Box(Modifier.fillMaxSize()) {
                        val pageState = rememberContinueCarouselState(cardCount = { 1 })
                        ContinueCarousel(
                            cards = listOf(card(1L, medium = ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12))),
                            selectedId = "b1:audio",
                            state = pageState,
                            onSettled = {},
                            onOpenBook = { opened += it },
                            onResume = { resumed += it },
                            onShowContextMenu = { menus += it.uuid },
                            cardsPerPage = { _, _ -> 1 },
                        )
                    }
                }
            }
        }

        rule.onNodeWithTag(ContinueTestTags.cover("b1:audio"), useUnmergedTree = true).performClick()
        rule.onNodeWithTag(ContinueTestTags.title("b1:audio"), useUnmergedTree = true).performClick()
        rule.onNodeWithTag(ContinueTestTags.pill("b1:audio"), useUnmergedTree = true).performClick()
        rule.onNodeWithTag(ContinueTestTags.author("b1:audio"), useUnmergedTree = true).performClick()
        rule.onNodeWithTag(ContinueTestTags.author("b1:audio"), useUnmergedTree = true).performTouchInput { longClick() }
        rule.waitForIdle()

        assertEquals(2, opened.size)
        assertEquals(listOf("b1"), menus)
        assertEquals(2, resumed.size)
        assertEquals(4, haptics.virtualKeys)
    }

    @Test
    fun issue217_tc9_theRuleGetsTheCarouselsOwnWidth() {
        val widths = mutableListOf<Dp>()
        val scales = mutableListOf<Float>()
        val deviceScales = mutableListOf<Float>()
        setHalfWidthCarousel(widths, scales, deviceScales)
        rule.waitForIdle()

        val carousel = boundsOf(ContinueTestTags.CAROUSEL)
        val root = rule.onRoot().getBoundsInRoot()

        assertTrue("the carousel is narrower than the window", carousel.width < root.width)
        assertNear(carousel.width, widths.last())
    }

    @Test
    fun issue217_tc9_theRuleGetsTheFontScale() {
        val widths = mutableListOf<Dp>()
        val scales = mutableListOf<Float>()
        val deviceScales = mutableListOf<Float>()
        setHalfWidthCarousel(widths, scales, deviceScales)
        rule.waitForIdle()

        assertEquals(deviceScales.last(), scales.last(), 0f)
    }

    @Test
    fun issue217_tc10_twoCardsShareTheRowEqually() {
        setCarousel(fiveCards(), selected = "b1:epub", cardsPerPage = { _, _ -> 2 })
        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).assertIsDisplayed()

        val carousel = boundsOf(ContinueTestTags.CAROUSEL)
        val first = boundsOf(ContinueTestTags.card("b1:epub"))
        val second = boundsOf(ContinueTestTags.card("b2:epub"))

        assertNear(first.width, second.width)
        assertNear(first.height, second.height)
        assertTrue("the cards do not overlap", first.right < second.left)
        assertTrue("the gap is less than a card", second.left - first.right < first.width)
        assertNear(first.left - carousel.left, carousel.right - second.right)
    }

    @Test
    fun issue217_tc11_aLoneCardKeepsAFullPageCardWidthAtTheStart() {
        val cards = mutableStateOf(listOf(card(1L), card(2L), card(3L)))
        val selected = mutableStateOf<String?>("b1:epub")
        setHostedCarousel(cards, selected, mutableListOf(), feedBack = false, cardsPerPage = { _, _ -> 2 })
        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).assertIsDisplayed()

        val carousel = boundsOf(ContinueTestTags.CAROUSEL)
        val fullPageCard = boundsOf(ContinueTestTags.card("b1:epub"))

        rule.runOnIdle { selected.value = "b3:epub" }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
        val lone = boundsOf(ContinueTestTags.card("b3:epub"))

        assertNear(fullPageCard.width, lone.width)
        assertNear(fullPageCard.left - carousel.left, lone.left - carousel.left)
        assertTrue("the lone card does not stretch to the row", lone.width < carousel.width / 2)
    }

    @Test
    fun issue217_tc13_dotsCountPages() {
        setCarousel(fiveCards(), selected = "b1:epub", cardsPerPage = { _, _ -> 2 })

        rule.onAllNodesWithTag(ContinueTestTags.dot(0), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(ContinueTestTags.dot(1), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(ContinueTestTags.dot(2), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(ContinueTestTags.dot(3), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun issue217_tc15_swipingSelectsTheFirstCardOfThePage() {
        val reports = mutableListOf<String>()
        val cards = mutableStateOf(fiveCards())
        val selected = mutableStateOf<String?>("b1:epub")
        setHostedCarousel(cards, selected, reports, feedBack = true, cardsPerPage = { _, _ -> 2 })
        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).assertIsDisplayed()

        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.6f, 0f))
        }
        rule.waitForIdle()

        assertTrue("nothing settles while the finger is down", reports.isEmpty())

        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).performTouchInput { up() }
        rule.waitForIdle()

        assertEquals("b3:epub", reports.last())

        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).performTouchInput {
            down(center)
            moveBy(Offset(-width * 0.6f, 0f))
            up()
        }
        rule.waitForIdle()

        assertEquals("b5:epub", reports.last())
        assertTrue("b2 and b4 are never reported", reports.none { it == "b2:epub" || it == "b4:epub" })
    }

    @Test
    fun issue217_tc16_anExternalSelectionMovesToThePageHoldingItOnlyWhenNeeded() {
        val reports = mutableListOf<String>()
        val cards = mutableStateOf(fiveCards())
        val selected = mutableStateOf<String?>("b1:epub")
        setHostedCarousel(cards, selected, reports, feedBack = false, cardsPerPage = { _, _ -> 2 })
        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).assertIsDisplayed()

        rule.runOnIdle { selected.value = "b2:epub" }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).assertIsDisplayed()
        assertTrue("a selection on the settled page reports nothing", reports.isEmpty())

        rule.runOnIdle { selected.value = "b4:epub" }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.card("b4:epub"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("b3:epub"), reports)
    }

    @Test
    fun issue217_tc18_regroupingLandsOnThePageHoldingTheSelectionWithoutDrift() {
        val reports = mutableListOf<String>()
        val cards = mutableStateOf(fiveCards())
        val selected = mutableStateOf<String?>("b4:epub")
        val perPage = mutableStateOf(1)
        setHostedCarousel(cards, selected, reports, feedBack = true, cardsPerPage = { _, _ -> perPage.value })
        rule.onNodeWithTag(ContinueTestTags.card("b4:epub"), useUnmergedTree = true).assertIsDisplayed()
        val oneUpWidth = boundsOf(ContinueTestTags.card("b4:epub")).width

        rule.runOnIdle { perPage.value = 2 }
        rule.waitForIdle()

        assertTrue("b4 narrows at two per page", boundsOf(ContinueTestTags.card("b4:epub")).width < oneUpWidth)
        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.card("b4:epub"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("b3:epub"), reports)

        rule.runOnIdle { perPage.value = 1 }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
        assertEquals(listOf("b3:epub"), reports)
    }

    @Test
    fun issue217_tc19_aShrinkingListNeverReportsAnUnknownCard() {
        val reports = mutableListOf<String>()
        val cards = mutableStateOf(fiveCards())
        val selected = mutableStateOf<String?>("b5:epub")
        setHostedCarousel(cards, selected, reports, feedBack = true, cardsPerPage = { _, _ -> 2 })
        rule.onNodeWithTag(ContinueTestTags.card("b5:epub"), useUnmergedTree = true).assertIsDisplayed()

        val shrunk = listOf(card(1L), card(2L))
        rule.runOnIdle {
            cards.value = shrunk
            selected.value = selectionAfterRefresh(selected.value, shrunk.map { it.id })
        }
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).assertIsDisplayed()
        assertTrue("every report is in the list", reports.all { id -> shrunk.any { it.id == id } })

        val replaced = listOf(card(9L), card(8L), card(1L))
        rule.runOnIdle {
            cards.value = replaced
            selected.value = selectionAfterRefresh(selected.value, replaced.map { it.id })
        }
        rule.waitForIdle()

        assertTrue("every report is in the list", reports.all { id -> replaced.any { it.id == id } })
    }

    @Test
    fun issue217_tc21_recreationIntoOneUpRestoresThePageHoldingTheSelection() {
        var wide = true
        val reports = mutableListOf<String>()
        val cards = mutableStateOf(fiveCards())
        val selected = mutableStateOf<String?>("b3:epub")
        val tester = StateRestorationTester(rule)
        tester.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val pageState = rememberContinueCarouselState(cardCount = { cards.value.size })
                    ContinueCarousel(
                        cards = cards.value,
                        selectedId = selected.value,
                        state = pageState,
                        onSettled = {
                            reports += it
                            selected.value = it
                        },
                        onOpenBook = {},
                        onResume = {},
                        onShowContextMenu = {},
                        cardsPerPage = { _, _ -> if (wide) 2 else 1 },
                    )
                }
            }
        }
        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.card("b4:epub"), useUnmergedTree = true).assertIsDisplayed()

        rule.runOnIdle {
            wide = false
            reports.clear()
        }
        tester.emulateSavedInstanceStateRestore()
        rule.waitForIdle()

        rule.onNodeWithTag(ContinueTestTags.card("b3:epub"), useUnmergedTree = true).assertIsDisplayed()
        // The pager composes only the page on screen, so the cards of the other pages are not in the tree at all.
        rule.onAllNodesWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).assertCountEquals(0)
        rule.onAllNodesWithTag(ContinueTestTags.card("b4:epub"), useUnmergedTree = true).assertCountEquals(0)
        assertTrue("only the selected card is reported", reports.all { it == "b3:epub" })
    }

    @Test
    fun issue217_tc22_eachCardOnASharedPageHasOneSetOfTags() {
        setCarousel(listOf(card(1L), card(2L)), selected = "b1:epub", cardsPerPage = { _, _ -> 2 })

        val tags = listOf(
            ContinueTestTags.card("b1:epub"), ContinueTestTags.card("b2:epub"),
            ContinueTestTags.cover("b1:epub"), ContinueTestTags.cover("b2:epub"),
            ContinueTestTags.title("b1:epub"), ContinueTestTags.title("b2:epub"),
            ContinueTestTags.author("b1:epub"), ContinueTestTags.author("b2:epub"),
            ContinueTestTags.pill("b1:epub"), ContinueTestTags.pill("b2:epub"),
        )
        tags.forEach { tag ->
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(1)
        }
    }

    @Test
    fun issue217_tc22_tapsRouteToTheCardTheyLandOn() {
        val opened = mutableListOf<String>()
        val resumed = mutableListOf<String>()
        val menus = mutableListOf<String>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val pageState = rememberContinueCarouselState(cardCount = { 2 })
                    ContinueCarousel(
                        cards = listOf(card(1L), card(2L)),
                        selectedId = "b1:epub",
                        state = pageState,
                        onSettled = {},
                        onOpenBook = { opened += it },
                        onResume = { resumed += it },
                        onShowContextMenu = { menus += it.uuid },
                        cardsPerPage = { _, _ -> 2 },
                    )
                }
            }
        }

        // A forced two-up page on a narrow device leaves the text column no width, so tap only what a card always
        // has: its cover, and its own edge (inside the card's padding, clear of the cover).
        val edge = Offset(4f, 4f)
        rule.onNodeWithTag(ContinueTestTags.cover("b2:epub"), useUnmergedTree = true).performClick()
        rule.onNodeWithTag(ContinueTestTags.cover("b1:epub"), useUnmergedTree = true).performClick()
        rule.onNodeWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).performTouchInput { click(edge) }
        rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).performTouchInput { click(edge) }
        rule.onNodeWithTag(ContinueTestTags.card("b2:epub"), useUnmergedTree = true).performTouchInput { longClick(edge) }
        rule.waitForIdle()

        assertEquals(listOf("b2", "b1"), opened)
        assertEquals(listOf("b2:epub", "b1:epub"), resumed)
        assertEquals(listOf("b2"), menus)
    }

    private fun setCarousel(
        cards: List<ContinueCard>,
        selected: String,
        onSettled: (String) -> Unit = {},
        cardsPerPage: (Dp, Float) -> Int = { _, _ -> 1 },
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val pageState = rememberContinueCarouselState(cardCount = { cards.size })
                    ContinueCarousel(
                        cards = cards,
                        selectedId = selected,
                        state = pageState,
                        onSettled = onSettled,
                        onOpenBook = {},
                        onResume = {},
                        onShowContextMenu = {},
                        cardsPerPage = cardsPerPage,
                    )
                }
            }
        }
    }

    /** A carousel whose cards, selection and rule the test holds; a report is added to [reports], and to the selection when [feedBack]. */
    private fun setHostedCarousel(
        cards: MutableState<List<ContinueCard>>,
        selected: MutableState<String?>,
        reports: MutableList<String>,
        feedBack: Boolean,
        cardsPerPage: (Dp, Float) -> Int,
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val pageState = rememberContinueCarouselState(cardCount = { cards.value.size })
                    ContinueCarousel(
                        cards = cards.value,
                        selectedId = selected.value,
                        state = pageState,
                        onSettled = {
                            reports += it
                            if (feedBack) selected.value = it
                        },
                        onOpenBook = {},
                        onResume = {},
                        onShowContextMenu = {},
                        cardsPerPage = cardsPerPage,
                    )
                }
            }
        }
    }

    /** A carousel half the window wide, recording the rule's widths and font scales and the device's font scale. */
    private fun setHalfWidthCarousel(widths: MutableList<Dp>, scales: MutableList<Float>, deviceScales: MutableList<Float>) {
        rule.setContent {
            deviceScales += LocalDensity.current.fontScale
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth(0.5f)) {
                        val pageState = rememberContinueCarouselState(cardCount = { 5 })
                        ContinueCarousel(
                            cards = fiveCards(),
                            selectedId = "b1:epub",
                            state = pageState,
                            onSettled = {},
                            onOpenBook = {},
                            onResume = {},
                            onShowContextMenu = {},
                            cardsPerPage = { width, scale ->
                                widths += width
                                scales += scale
                                1
                            },
                        )
                    }
                }
            }
        }
    }

    private fun boundsOf(tag: String): DpRect = rule.onNodeWithTag(tag, useUnmergedTree = true).getBoundsInRoot()

    /** Asserts two lengths agree within a dp, which is what the layout can differ by on any device. */
    private fun assertNear(expected: Dp, actual: Dp) {
        assertTrue("expected $expected, was $actual", abs(actual.value - expected.value) <= 1f)
    }

    /** Counts the [HapticFeedbackType.VirtualKey] pulses, which the card's taps make. */
    private class CountingHaptics : HapticFeedback {
        var virtualKeys = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            if (hapticFeedbackType == HapticFeedbackType.VirtualKey) virtualKeys++
        }
    }

    private companion object {
        fun fiveCards(): List<ContinueCard> = (1L..5L).map { card(it) }


        fun card(
            id: Long,
            medium: ProgressFormat = ProgressFormat.Reading,
            fraction: Double? = 0.5,
            position: PositionLabel = PositionLabel.PercentRead(50),
            lastOpened: RelativeAge = RelativeAge.Hours(2),
            title: String = "Book $id",
        ): ContinueCard = ContinueCard(
            id = "b$id:${if (medium == ProgressFormat.Listening) "audio" else "epub"}",
            book = book(id, title),
            medium = medium,
            fraction = fraction,
            position = position,
            lastOpened = lastOpened,
            resumeFileId = null,
        )

        fun book(id: Long, title: String) = Book(
            id = id,
            uniqueIdentifier = "b$id",
            filename = "book-$id.epub",
            title = title,
            creators = listOf(Creator("Author $id", null, null, null)),
            coverUrl = null,
            accent = null,
            formats = listOf("epub", "m4b"),
            series = null,
            seriesIndex = null,
            hasPhysical = false,
            addedAt = null,
            lastInteractedAt = null,
            bookFiles = emptyList(),
        )
    }
}

private val DpRect.width: Dp get() = right - left

private val DpRect.height: Dp get() = bottom - top
