package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.feature.library.ContinueCard
import com.omnibus.omnibus.feature.library.PositionLabel
import com.omnibus.omnibus.feature.library.RelativeAge
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class ContinueCarouselTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun issue22_tc27_dotsOnlyWithTwoOrMoreCardsAndOneSelectedAndHiddenFromAccessibility() {
        setCarousel(listOf(card(1L), card(2L), card(3L)), selected = "b1:epub")

        rule.onNodeWithTag(ContinueTestTags.DOTS, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        val first = dotWidth(0)
        val second = dotWidth(1)
        val third = dotWidth(2)
        assertTrue("the selected dot is wider than the others", first > second + HALF_DP)
        assertTrue("the resting dots are the same width", abs((second - third).value) <= HALF_DP.value)
        rule.onNodeWithTag(ContinueTestTags.dot(0), useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test
    fun issue22_tc27_oneCardShowsNoDots() {
        setCarousel(listOf(card(1L)), selected = "b1:epub")

        rule.onNodeWithTag(ContinueTestTags.DOTS, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun issue22_tc28_dotsAndSelectionFollowTheSettledPageOnly() {
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

        assertTrue("the first dot stays the widest while the finger is down", dotWidth(0) > dotWidth(1) + HALF_DP)
        assertTrue("nothing settles while the finger is down", settled.all { it == "b1:epub" })

        rule.onNodeWithTag(ContinueTestTags.CAROUSEL, useUnmergedTree = true).performTouchInput { up() }
        rule.waitForIdle()

        assertTrue("the second dot is the widest after the swipe settles", dotWidth(1) > dotWidth(0) + HALF_DP)
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
                    val pageState = rememberContinueCarouselState(pageCount = { cards.value.size })
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
    fun issue22_tc30_listeningCardShowsChapterAndPlay() {
        setCarousel(
            listOf(
                card(1L, medium = ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12)),
            ),
            selected = "b1:audio",
        )

        rule.onNodeWithText("CONTINUE LISTENING", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Chapter 3 of 12", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Play", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.barTrack("b1:audio"), useUnmergedTree = true).assertExists()
    }

    @Test
    fun issue22_tc30_readingCardShowsPercentAndRead() {
        setCarousel(
            listOf(card(1L, fraction = 0.42, position = PositionLabel.PercentRead(42))),
            selected = "b1:epub",
        )

        rule.onNodeWithText("CONTINUE READING", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("42% read", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("Read", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun issue22_tc30_cardWithoutFractionShowsLastOpenedAndNoBar() {
        setCarousel(
            listOf(card(1L, fraction = null, position = PositionLabel.InProgress, lastOpened = RelativeAge.Minutes(5))),
            selected = "b1:epub",
        )

        rule.onNodeWithText("Last opened 5 min. ago", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithText("In progress", useUnmergedTree = true).assertIsDisplayed()
        rule.onNodeWithTag(ContinueTestTags.barTrack("b1:epub"), useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun issue22_tc30_justNowShowsInLastOpened() {
        setCarousel(
            listOf(card(1L, fraction = null, lastOpened = RelativeAge.JustNow)),
            selected = "b1:epub",
        )

        rule.onNodeWithText("Last opened just now", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun issue22_tc30_timeLeftShowsAsMinutesLeft() {
        setCarousel(
            listOf(card(2L, medium = ProgressFormat.Listening, fraction = 0.3, position = PositionLabel.TimeLeft(2000))),
            selected = "b2:audio",
        )

        rule.onNodeWithText("33m left", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun issue22_tc30_partShowsPartOfTotal() {
        setCarousel(
            listOf(card(3L, medium = ProgressFormat.Listening, fraction = 0.3, position = PositionLabel.Part(2, 4))),
            selected = "b3:audio",
        )

        rule.onNodeWithText("Part 2 of 4", useUnmergedTree = true).assertIsDisplayed()
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
                        val pageState = rememberContinueCarouselState(pageCount = { 1 })
                        ContinueCarousel(
                            cards = listOf(card(1L, medium = ProgressFormat.Listening, fraction = 0.4, position = PositionLabel.Chapter(3, 12))),
                            selectedId = "b1:audio",
                            state = pageState,
                            onSettled = {},
                            onOpenBook = { opened += it },
                            onResume = { resumed += it },
                            onShowContextMenu = { menus += it.uuid },
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
    fun issue22_tc32_titleKeepsTwoLinesAtLargeFontScale() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale = 2f)) {
                    Box(Modifier.fillMaxSize()) {
                        val pageState = rememberContinueCarouselState(pageCount = { 1 })
                        ContinueCarousel(
                            cards = listOf(card(1L, title = LONG_TITLE)),
                            selectedId = "b1:epub",
                            state = pageState,
                            onSettled = {},
                            onOpenBook = {},
                            onResume = {},
                            onShowContextMenu = {},
                        )
                    }
                }
            }
        }

        val title = rule.textLayoutOf(ContinueTestTags.title("b1:epub"))
        assertEquals(2, title.lineCount)
        val card = rule.onNodeWithTag(ContinueTestTags.card("b1:epub"), useUnmergedTree = true).getUnclippedBoundsInRoot()
        val titleBounds = rule.onNodeWithTag(ContinueTestTags.title("b1:epub"), useUnmergedTree = true)
            .getUnclippedBoundsInRoot()
        assertTrue("the title sits inside the card", titleBounds.top >= card.top && titleBounds.bottom <= card.bottom)
    }

    private fun setCarousel(
        cards: List<ContinueCard>,
        selected: String,
        onSettled: (String) -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    val pageState = rememberContinueCarouselState(pageCount = { cards.size })
                    ContinueCarousel(
                        cards = cards,
                        selectedId = selected,
                        state = pageState,
                        onSettled = onSettled,
                        onOpenBook = {},
                        onResume = {},
                        onShowContextMenu = {},
                    )
                }
            }
        }
    }

    private fun dotWidth(index: Int): Dp =
        rule.onNodeWithTag(ContinueTestTags.dot(index), useUnmergedTree = true).getUnclippedBoundsInRoot().let {
            it.right - it.left
        }

    private fun ComposeContentTestRule.textLayoutOf(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    /** Counts the [HapticFeedbackType.VirtualKey] pulses, which the card's taps make. */
    private class CountingHaptics : HapticFeedback {
        var virtualKeys = 0

        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
            if (hapticFeedbackType == HapticFeedbackType.VirtualKey) virtualKeys++
        }
    }

    private companion object {
        /** Half a dp of slack, to absorb sub-pixel rounding in the measured bounds. */
        val HALF_DP = 0.5.dp

        /** A title long enough to need more than two lines at any card width. */
        const val LONG_TITLE = "The Long Way to a Small, Angry Planet: A Novel of the Wayfarers"

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
