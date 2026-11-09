package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithTag
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.data.reader.remote.BookFileSource
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.feature.reader.ReaderUIState
import com.omnibus.omnibus.feature.reader.withMenuRowChosen
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The reader's settings sheet. The sheet is hosted over a hoisted state, so each control's
 * callback is checked against the value it was given. Assertions are on state and relative
 * layout, never on fixed sizes, so the cases hold on the 320x640 CI AVD and on a phone.
 */
class ReaderSettingsSheetTest {

    @get:Rule
    val rule = createComposeRule()

    /**
     * Hosts the sheet over a settings state the test can read and change. Every callback is
     * recorded in [changes], so a case can check exactly which setting moved.
     */
    private fun host(
        initial: ReaderSettings = ReaderSettings(),
        changes: MutableList<ReaderSettings> = mutableListOf(),
    ) {
        val settings = mutableStateOf(initial)
        rule.setContent {
            OmnibusTheme {
                ReaderSettingsSheet(
                    settings = settings.value,
                    accent = readerSheetAccent(ReaderBookState.Ready(title = "Test Book"), OmnibusTheme.palette),
                    onSettingsChanged = {
                        settings.value = it
                        changes += it
                    },
                    onDismiss = {},
                )
            }
        }
    }

    /** A control, scrolled into view first: on a short window the lower groups sit below the fold. */
    private fun node(tag: String) = rule.onNodeWithTag(tag).performScrollTo()

    @Test
    fun tappingAThemeSwatchChangesOnlyTheTheme() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings(theme = ReaderTheme.Dark)
        host(settings, changes)

        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).performClick()

        assertEquals(listOf(settings.copy(theme = ReaderTheme.Light)), changes)
    }

    @Test
    fun tappingATypefaceChangesOnlyTheTypeface() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings()
        host(settings, changes)

        node(ReaderSettingsSheetTags.typeface(ReaderTypeface.Classic)).performClick()

        assertEquals(listOf(settings.copy(typeface = ReaderTypeface.Classic)), changes)
    }

    @Test
    fun sizeStepperStepsByOne() {
        val changes = mutableListOf<ReaderSettings>()
        host(ReaderSettings(fontSize = 19), changes)

        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("19")
        node(ReaderSettingsSheetTags.SIZE_INCREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("20")
        node(ReaderSettingsSheetTags.SIZE_DECREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_DECREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("18")
        assertEquals(listOf(20, 19, 18), changes.map { it.fontSize })
    }

    @Test
    fun lineHeightStepperStepsByOneTenth() {
        val changes = mutableListOf<ReaderSettings>()
        host(ReaderSettings(lineHeight = 1.6), changes)

        node(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE).assertTextEquals("1.6")
        node(ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE).performClick()

        assertEquals(1.7, changes.single().lineHeight, 0.0)
        node(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE).assertTextEquals("1.7")
    }

    @Test
    fun lineHeightIsShownWithADotUnderACommaDecimalLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            host(ReaderSettings(lineHeight = 1.6))

            node(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE).assertTextEquals("1.6")
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun tappingAMarginChangesOnlyTheMargins() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings(margins = ReaderMargins.Normal)
        host(settings, changes)

        node(ReaderSettingsSheetTags.margins(ReaderMargins.Wide)).performClick()

        assertEquals(listOf(settings.copy(margins = ReaderMargins.Wide)), changes)
    }

    @Test
    fun tc343_tappingAPageTurnChangesOnlyThePageTurn() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings(pageTurn = ReaderPageTurn.Slide)
        host(settings, changes)

        node(ReaderSettingsSheetTags.pageTurn(ReaderPageTurn.Curl)).performClick()

        assertEquals(listOf(settings.copy(pageTurn = ReaderPageTurn.Curl)), changes)
    }

    @Test
    fun tc344_pageTurnSegmentsAreRadioButtons() {
        host(ReaderSettings(pageTurn = ReaderPageTurn.Curl))

        ReaderPageTurn.entries.forEach { mode ->
            node(ReaderSettingsSheetTags.pageTurn(mode))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        node(ReaderSettingsSheetTags.pageTurn(ReaderPageTurn.Curl)).assertIsSelected()
        node(ReaderSettingsSheetTags.pageTurn(ReaderPageTurn.Slide)).assertIsNotSelected()
        node(ReaderSettingsSheetTags.pageTurn(ReaderPageTurn.None)).assertIsNotSelected()
    }

    @Test
    fun justifySwitchReflectsTheSettingAndTogglesIt() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings(justify = true)
        host(settings, changes)

        node(ReaderSettingsSheetTags.JUSTIFY).assertIsOn()
        node(ReaderSettingsSheetTags.JUSTIFY).performClick()

        assertEquals(listOf(settings.copy(justify = false)), changes)
        node(ReaderSettingsSheetTags.JUSTIFY).assertIsOff()
    }

    @Test
    fun controlsShowTheUpdatedSettingsInPlace() {
        host(ReaderSettings(theme = ReaderTheme.Dark))

        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).performClick()
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).assertIsSelected()
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Dark)).assertIsNotSelected()
    }

    @Test
    fun theSheetFromTheMenuClosesOnDone() {
        val uiState = mutableStateOf(
            ReaderUIState(
                book = ReaderBookState.Ready(title = "Moby Dick"),
                loadStatus = com.omnibus.omnibus.feature.reader.ui.ReaderLoadStatus.Ready,
                chromeVisible = true,
                menuOpen = true,
                menuRows = listOf(ReaderMenuRow.Settings),
            ),
        )
        val chosen = mutableListOf<ReaderMenuRow>()
        var dismissed = 0
        rule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                OmnibusTheme {
                    ReaderScreenContent(
                        uiState = uiState.value,
                        bookUuid = "book-1",
                        controller = ReaderController(),
                        assetLoaderFactory = ReaderAssetLoaderFactory(
                            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext,
                            Dispatchers.Unconfined,
                            BookFileSource { BookFileResult.NotFound },
                        ),
                        commands = emptyFlow(),
                        onPageEvent = {},
                        onSheetDismissed = {
                            dismissed++
                            uiState.value = uiState.value.copy(sheet = null)
                        },
                        onMenuOpened = {},
                        onMenuDismissed = {},
                        onMenuRowChosen = {
                            chosen += it
                            uiState.value = uiState.value.withMenuRowChosen(it)
                        },
                        onTocEntryChosen = {},
                        onRetryBookRequested = {},
                        onReaderClosed = {},
                        onReaderStopped = {},
                        onSyncOfferAccepted = {},
                        onSyncOfferDismissed = {},
                        onClose = {},
                    )
                }
            }
        }

        rule.onNodeWithTag(ReaderChromeTags.menuRow(ReaderMenuRow.Settings)).performClick()
        assertEquals(listOf(ReaderMenuRow.Settings), chosen)
        rule.onNodeWithTag(ReaderSettingsSheetTags.SHEET).assertIsDisplayed()

        node(ReaderSettingsSheetTags.DONE).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag(ReaderSettingsSheetTags.SHEET).fetchSemanticsNodes().isEmpty() }
        assertEquals(1, dismissed)
    }
}
