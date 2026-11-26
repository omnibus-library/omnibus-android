package com.omnibus.omnibus.feature.libraries.add

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.libraries.Monogram
import com.omnibus.omnibus.feature.libraries.switching.BOOKCASE_DROP_IN
import com.omnibus.omnibus.feature.libraries.switching.Bookcase
import com.omnibus.omnibus.feature.libraries.switching.BookcaseGeometry
import com.omnibus.omnibus.feature.libraries.switching.BookcaseSpine
import com.omnibus.omnibus.feature.libraries.switching.BookcaseState
import com.omnibus.omnibus.feature.libraries.switching.BookcaseTransition
import com.omnibus.omnibus.feature.libraries.switching.LocalRemoveAnimations
import com.omnibus.omnibus.feature.libraries.switching.bookcaseSpineTag
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The drop-in of an added library's spine (#371). Relative asserts only. */
class BookcaseDropInTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** The bottom edge of [libraryId]'s spine, in px from the root. A spine's bottom sits on the plank when level. */
    private fun bottomOf(libraryId: String): Float =
        rule.onNodeWithTag(bookcaseSpineTag(libraryId), useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.bottom

    @Test
    fun issue371_tc21_removeAnimationsDropsInOneStep() {
        rule.mainClock.autoAdvance = false
        val state = mutableStateOf(dropping())
        rule.setContent {
            OmnibusTheme {
                CompositionLocalProvider(LocalRemoveAnimations provides true) {
                    Bookcase(state = state.value)
                }
            }
        }

        // No clock advance: with remove-animations on, the new spine must already be on its slot.
        rule.waitForIdle()

        assertEquals(
            "the new spine lands level with the other",
            bottomOf("other").toDouble(),
            bottomOf("new").toDouble(),
            1.0,
        )
    }

    @Test
    fun issue371_tc22_aLiftDuringTheDropLandsTheSpine() {
        rule.mainClock.autoAdvance = false
        val raise = with(rule.density) { BookcaseGeometry.RAISE.toPx().toDouble() }
        val state = mutableStateOf(dropping())
        rule.setContent { OmnibusTheme { Bookcase(state = state.value) } }
        rule.mainClock.advanceTimeByFrame()

        // Part way through the drop, the switch goes ahead and lifts the new spine before the drop has finished.
        rule.mainClock.advanceTimeBy(BOOKCASE_DROP_IN.inWholeMilliseconds / 2)
        rule.runOnIdle {
            state.value = state.value.copy(transition = BookcaseTransition.Lift(fromId = "current", toId = "new"))
        }
        rule.mainClock.advanceTimeBy(BookcaseGeometry.LIFT.inWholeMilliseconds + 100)
        rule.mainClock.advanceTimeByFrame()

        assertEquals(
            "the new spine is raised above the current one after the lift",
            -raise,
            (bottomOf("new") - bottomOf("current")).toDouble(),
            1.0,
        )
    }

    /** Three spines: [current] raised, [other], and [new] just added at the end and dropping in. */
    private fun dropping() = BookcaseState(
        spines = listOf(spine("current", 100), spine("other", 90), spine("new", 110)),
        raisedLibraryId = "current",
        transition = BookcaseTransition.DropIn(libraryId = "new"),
    )

    private fun spine(id: String, heightDp: Int) = BookcaseSpine(
        libraryId = id,
        monogram = Monogram.Letter(id.uppercase()),
        height = heightDp.dp,
    )
}
