package com.omnibus.omnibus.feature.library.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.center
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
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

/** Paging, selection, taps, semantics and label wording. ContinueCarouselScreenshotTest covers how the cards and dots look. */
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
