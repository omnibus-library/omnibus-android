package com.omnibus.omnibus.feature.libraries.switching

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.libraries.Monogram
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The bookcase's raise and lift, and its reduced-motion path (#369). Relative asserts only. */
class BookcaseTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    /** The bottom edge of [libraryId]'s spine, in px from the root. A spine's bottom sits on the plank when level. */
    private fun bottomOf(libraryId: String): Float =
        rule.onNodeWithTag(bookcaseSpineTag(libraryId)).fetchSemanticsNode().boundsInRoot.bottom

    @Test
    fun issue369_tc31_theLiftMovesTheRaisedSpine() {
        rule.mainClock.autoAdvance = false
        val raise = with(rule.density) { BookcaseGeometry.RAISE.toPx().toDouble() }
        val state = mutableStateOf(
            BookcaseState(
                spines = listOf(spine("a", 100), spine("b", 90)),
                raisedLibraryId = "a",
                transition = BookcaseTransition.Still,
            ),
        )
        rule.setContent { OmnibusTheme { Bookcase(state = state.value) } }
        rule.mainClock.advanceTimeByFrame()

        assertEquals("a is raised above b before the lift", raise, bottomOf("b").toDouble() - bottomOf("a"), 1.0)

        rule.runOnIdle {
            state.value = state.value.copy(transition = BookcaseTransition.Lift(fromId = "a", toId = "b"))
        }
        rule.mainClock.advanceTimeBy(BookcaseGeometry.LIFT.inWholeMilliseconds + 100)
        rule.mainClock.advanceTimeByFrame()

        assertEquals("b is raised above a after the lift", -raise, bottomOf("b").toDouble() - bottomOf("a"), 1.0)
    }

    @Test
    fun issue369_tc34_removeAnimationsLiftsInOneStep() {
        rule.mainClock.autoAdvance = false
        val raise = with(rule.density) { BookcaseGeometry.RAISE.toPx().toDouble() }
        val state = mutableStateOf(
            BookcaseState(
                spines = listOf(spine("a", 100), spine("b", 90)),
                raisedLibraryId = "a",
                transition = BookcaseTransition.Still,
            ),
        )
        rule.setContent {
            OmnibusTheme {
                CompositionLocalProvider(LocalRemoveAnimations provides true) {
                    Bookcase(state = state.value)
                }
            }
        }
        rule.mainClock.advanceTimeByFrame()

        rule.runOnIdle {
            state.value = state.value.copy(transition = BookcaseTransition.Lift(fromId = "a", toId = "b"))
        }
        // Well short of the 450 ms half-lift: a lift that still animated would be part way through by now.
        rule.mainClock.advanceTimeBy(100)
        rule.mainClock.advanceTimeByFrame()

        assertEquals("b is raised at once", -raise, bottomOf("b").toDouble() - bottomOf("a"), 1.0)
    }

    private fun spine(id: String, heightDp: Int) = BookcaseSpine(
        libraryId = id,
        monogram = Monogram.Letter(id.uppercase()),
        height = heightDp.dp,
    )
}
