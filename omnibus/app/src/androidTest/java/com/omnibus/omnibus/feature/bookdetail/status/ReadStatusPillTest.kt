package com.omnibus.omnibus.feature.bookdetail.status

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.readstatus.ReadStatus
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import com.omnibus.omnibus.feature.bookdetail.stage.bookAccent
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.feature.library.ui.coverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The read status pill and its menu, rendered alone with recording callbacks. Each test sets its content once and
 * changes the state it shows through the returned [MutableState]. ReadStatusPillScreenshotTest covers how the pill
 * looks.
 */
class ReadStatusPillTest {

    @get:Rule
    val rule = createComposeRule()

    private val tone = testDetailBook().coverIdentity().tone
    private val accent = bookAccent(tone, lightGround = false)
    private val rim = statusRim(tone, lightGround = false)

    private val locale get() = InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]

    /** Epoch millis of a UTC wall-clock time. */
    private fun utc(year: Int, month: Int, day: Int, hour: Int): Long =
        GregorianCalendar(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private val finishedOn2025 = utc(2025, 10, 29, 12)
    private val now2025 = utc(2025, 11, 15, 12)

    /** The date the page should show for [finishedAt] at [now], in the device's locale and zone. */
    private fun expectedDate(finishedAt: Long, now: Long): String =
        BookDetailFormat.shortDate(finishedAt, now, locale, TimeZone.getDefault())

    /**
     * Sets the pill alone on a full-size host, in dark theme. Returns the state it shows, so a test can change it with
     * `rule.runOnIdle { state.value = … }`.
     */
    private fun setPill(
        initial: ReadStatusUIState,
        onSelect: (ReadStatus) -> Unit = {},
        reducedMotion: Boolean = true,
    ): MutableState<ReadStatusUIState> {
        val state = mutableStateOf(initial)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    ReadStatusPill(
                        uiState = state.value,
                        accent = accent,
                        rim = rim,
                        onSelect = onSelect,
                        reducedMotion = reducedMotion,
                    )
                }
            }
        }
        return state
    }

    private fun openMenu() {
        rule.onNodeWithTag(ReadStatusTestTags.PILL).performClick()
        rule.waitForIdle()
    }

    /** Closes the open menu with back, then opens it again. */
    private fun reopenMenu() {
        Espresso.pressBack()
        rule.waitForIdle()
        openMenu()
    }

    // TC-82
    @Test
    fun tc82_contentDescriptionNamesTheStatusOrSaysReadStatus() {
        val state = setPill(ReadStatusUIState(status = ReadStatus.Reading))
        rule.onNodeWithTag(ReadStatusTestTags.PILL).assertContentDescriptionEquals("Read status: Reading")

        rule.runOnIdle { state.value = ReadStatusUIState(status = null) }
        rule.onNodeWithTag(ReadStatusTestTags.PILL).assertContentDescriptionEquals("Read status")
    }

    // TC-84
    @Test
    fun tc84_pillIsAButtonWithAClickAction() {
        setPill(ReadStatusUIState(status = ReadStatus.Unread))

        rule.onNodeWithTag(ReadStatusTestTags.PILL)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    // TC-86
    @Test
    fun tc86_tappingThePillOpensTheMenuWithThreeRows() {
        setPill(ReadStatusUIState(status = ReadStatus.Reading))

        openMenu()

        rule.onNodeWithTag(ReadStatusTestTags.MENU).assertExists()
        rule.onNodeWithTag(ReadStatusTestTags.MENU).assertContentDescriptionEquals("Read status")
        ReadStatus.entries.forEach { status ->
            rule.onAllNodesWithTag(ReadStatusTestTags.row(status)).assertCountEquals(1)
        }
    }

    // TC-88
    @Test
    fun tc88_onlyTheCurrentRowIsChecked() {
        val state = setPill(ReadStatusUIState(status = ReadStatus.Unread))
        val statuses = listOf<ReadStatus?>(ReadStatus.Unread, ReadStatus.Reading, ReadStatus.Finished, null)

        statuses.forEachIndexed { index, current ->
            rule.runOnIdle { state.value = ReadStatusUIState(status = current) }
            if (index > 0) reopenMenu() else openMenu()

            ReadStatus.entries.forEach { status ->
                rule.onNodeWithTag(ReadStatusTestTags.row(status))
                    .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, status == current))
            }
            if (current == null) {
                rule.onAllNodesWithTag(ReadStatusTestTags.CHECK, useUnmergedTree = true).assertCountEquals(0)
            } else {
                rule.onAllNodesWithTag(ReadStatusTestTags.CHECK, useUnmergedTree = true).assertCountEquals(1)
                rule.onNodeWithTag(ReadStatusTestTags.CHECK, useUnmergedTree = true)
                    .assert(hasAnyAncestor(hasTestTag(ReadStatusTestTags.row(current))))
            }
        }
    }

    // TC-89
    @Test
    fun tc89_finishedRowShowsTheShortDate() {
        setPill(ReadStatusUIState(status = ReadStatus.Finished, finishedAtMillis = finishedOn2025, nowMillis = now2025))
        openMenu()

        val date = expectedDate(finishedOn2025, now2025)
        rule.onNodeWithTag(ReadStatusTestTags.FINISHED_DATE, useUnmergedTree = true).assertTextEquals(date)
        rule.onNodeWithTag(ReadStatusTestTags.FINISHED_DATE, useUnmergedTree = true)
            .assert(hasAnyAncestor(hasTestTag(ReadStatusTestTags.row(ReadStatus.Finished))))
        assertFalse(date.contains("2025"))
    }

    // TC-91
    @Test
    fun tc91_noDateUnlessFinishedWithAKnownDate() {
        val state = setPill(ReadStatusUIState(status = ReadStatus.Unread))
        val shown = listOf(
            ReadStatusUIState(status = ReadStatus.Unread, finishedAtMillis = finishedOn2025, nowMillis = now2025),
            ReadStatusUIState(status = ReadStatus.Reading, finishedAtMillis = finishedOn2025, nowMillis = now2025),
            ReadStatusUIState(status = null),
            ReadStatusUIState(status = ReadStatus.Finished, finishedAtMillis = null, nowMillis = now2025),
        )

        shown.forEachIndexed { index, uiState ->
            rule.runOnIdle { state.value = uiState }
            if (index > 0) reopenMenu() else openMenu()

            rule.onAllNodesWithTag(ReadStatusTestTags.FINISHED_DATE, useUnmergedTree = true).assertCountEquals(0)
        }
    }

    // TC-92
    @Test
    fun tc92_rowsHaveTheirLabelsAsDescriptions() {
        setPill(ReadStatusUIState(status = ReadStatus.Finished, finishedAtMillis = finishedOn2025, nowMillis = now2025))
        openMenu()

        rule.onNodeWithTag(ReadStatusTestTags.row(ReadStatus.Unread)).assertContentDescriptionEquals("Unread")
        rule.onNodeWithTag(ReadStatusTestTags.row(ReadStatus.Reading)).assertContentDescriptionEquals("Reading")
        rule.onNodeWithTag(ReadStatusTestTags.row(ReadStatus.Finished))
            .assertContentDescriptionEquals(
                detailText(R.string.book_detail_status_finished_on_cd, expectedDate(finishedOn2025, now2025)),
            )
    }

    // TC-93
    @Test
    fun tc93_rowsAreRadioButtonsWithAClickAction() {
        setPill(ReadStatusUIState(status = ReadStatus.Reading))
        openMenu()

        ReadStatus.entries.forEach { status ->
            rule.onNodeWithTag(ReadStatusTestTags.row(status))
                .assertHasClickAction()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
    }

    // TC-94
    @Test
    fun tc94_choosingADifferentStatusCallsOnSelectAndCloses() {
        val picked = mutableListOf<ReadStatus>()
        setPill(ReadStatusUIState(status = ReadStatus.Reading), onSelect = { picked += it })

        openMenu()
        rule.onNodeWithTag(ReadStatusTestTags.row(ReadStatus.Finished)).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(ReadStatusTestTags.MENU).assertDoesNotExist()
        assertEquals(listOf(ReadStatus.Finished), picked)
    }

    // TC-95
    @Test
    fun tc95_choosingTheCurrentStatusClosesWithoutOnSelect() {
        val picked = mutableListOf<ReadStatus>()
        setPill(ReadStatusUIState(status = ReadStatus.Reading), onSelect = { picked += it })

        openMenu()
        rule.onNodeWithTag(ReadStatusTestTags.row(ReadStatus.Reading)).performClick()
        rule.waitForIdle()

        rule.onNodeWithTag(ReadStatusTestTags.MENU).assertDoesNotExist()
        assertTrue(picked.isEmpty())
    }

    // TC-96
    @Test
    fun tc96_backDismissesTheMenuWithoutOnSelect() {
        val picked = mutableListOf<ReadStatus>()
        setPill(ReadStatusUIState(status = ReadStatus.Reading), onSelect = { picked += it })
        openMenu()

        Espresso.pressBack()
        rule.waitForIdle()

        rule.onNodeWithTag(ReadStatusTestTags.MENU).assertDoesNotExist()
        assertTrue(picked.isEmpty())
    }

    // TC-97
    @Test
    fun tc97_reducedMotionSwapsTheLabelWithoutAnimating() {
        rule.mainClock.autoAdvance = false
        val state = setPill(ReadStatusUIState(status = ReadStatus.Unread), reducedMotion = true)
        rule.waitForIdle()

        rule.runOnIdle { state.value = ReadStatusUIState(status = ReadStatus.Reading) }
        rule.mainClock.advanceTimeByFrame()

        rule.onNodeWithText("Reading").assertExists()
        rule.onNodeWithText("Unread").assertDoesNotExist()
    }

    // TC-98
    @Test
    fun tc98_offlineFailurePostsTheOfflineMessageOnce() {
        val recorder = SectionContextRecorder()
        val context = testSectionContext(recorder = recorder)
        var shown = 0
        rule.setContent {
            ReadStatusSaveMessage(
                failure = ReadStatusSaveFailure.Offline,
                onShowMessage = context.onShowMessage,
                onShown = { shown++ },
            )
        }
        rule.waitForIdle()

        assertEquals(listOf("No connection. Your read status wasn't saved."), recorder.messages.map { it.text })
        assertEquals(1, shown)
    }

    // TC-99
    @Test
    fun tc99_otherFailurePostsTheGenericMessage() {
        val recorder = SectionContextRecorder()
        val context = testSectionContext(recorder = recorder)
        rule.setContent {
            ReadStatusSaveMessage(
                failure = ReadStatusSaveFailure.Other,
                onShowMessage = context.onShowMessage,
                onShown = {},
            )
        }
        rule.waitForIdle()

        assertEquals(listOf("Your read status wasn't saved."), recorder.messages.map { it.text })
    }

    // TC-100
    @Test
    fun tc100_noMessageForNullAndOneMessagePerFailure() {
        val recorder = SectionContextRecorder()
        val context = testSectionContext(recorder = recorder)
        var failure by mutableStateOf<ReadStatusSaveFailure?>(null)
        var recompositions by mutableIntStateOf(0)
        rule.setContent {
            Column {
                Text("recomposed $recompositions")
                ReadStatusSaveMessage(
                    failure = failure,
                    onShowMessage = context.onShowMessage,
                    onShown = {},
                )
            }
        }
        rule.waitForIdle()
        assertTrue(recorder.messages.isEmpty())

        rule.runOnIdle { failure = ReadStatusSaveFailure.Other }
        rule.waitForIdle()
        rule.runOnIdle { recompositions++ }
        rule.waitForIdle()

        assertEquals(1, recorder.messages.size)
    }
}
