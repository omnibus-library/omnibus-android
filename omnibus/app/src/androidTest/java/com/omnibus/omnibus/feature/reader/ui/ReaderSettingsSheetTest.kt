package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assert
import androidx.test.espresso.Espresso
import com.omnibus.omnibus.data.reader.BookFileResult
import com.omnibus.omnibus.data.reader.ReaderAssetLoaderFactory
import com.omnibus.omnibus.data.reader.ReaderMargins
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
    ): MutableState<ReaderSettings> {
        val settings = mutableStateOf(initial)
        rule.setContent {
            OmnibusTheme {
                ReaderSettingsSheet(
                    settings = settings.value,
                    onSettingsChanged = {
                        settings.value = it
                        changes += it
                    },
                    onDismiss = {},
                )
            }
        }
        return settings
    }

    /** A control, scrolled into view first: on a short window the lower groups sit below the fold. */
    private fun node(tag: String) = rule.onNodeWithTag(tag).performScrollTo()

    @Test
    fun showsTheTitleDoneAndEveryGroupAndControl() {
        host()

        rule.onNodeWithText("Reading").fetchSemanticsNode()
        node(ReaderSettingsSheetTags.DONE).fetchSemanticsNode()
        rule.onNodeWithText("PAGE").fetchSemanticsNode()
        rule.onNodeWithText("TYPE").fetchSemanticsNode()
        rule.onNodeWithText("LAYOUT").fetchSemanticsNode()

        ReaderTheme.entries.forEach { node(ReaderSettingsSheetTags.swatch(it)).performScrollTo().fetchSemanticsNode() }
        ReaderTypeface.entries.forEach { node(ReaderSettingsSheetTags.typeface(it)).performScrollTo().fetchSemanticsNode() }
        node(ReaderSettingsSheetTags.SIZE_DECREASE).performScrollTo().fetchSemanticsNode()
        node(ReaderSettingsSheetTags.SIZE_INCREASE).performScrollTo().fetchSemanticsNode()
        node(ReaderSettingsSheetTags.LINE_HEIGHT_DECREASE).performScrollTo().fetchSemanticsNode()
        node(ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE).performScrollTo().fetchSemanticsNode()
        ReaderMargins.entries.forEach { node(ReaderSettingsSheetTags.margins(it)).performScrollTo().fetchSemanticsNode() }
        node(ReaderSettingsSheetTags.JUSTIFY).performScrollTo().fetchSemanticsNode()
    }

    @Test
    fun themeSwatchesShowOneSelectedAndTappingOneChangesOnlyTheTheme() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings(theme = ReaderTheme.Dark)
        host(settings, changes)

        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Dark)).assertIsSelected()
        listOf(ReaderTheme.Light, ReaderTheme.Sepia, ReaderTheme.Black).forEach {
            node(ReaderSettingsSheetTags.swatch(it)).assertIsNotSelected()
        }
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).assertTextEquals("Light")
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Sepia)).assertTextEquals("Sepia")
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Dark)).assertTextEquals("Dark")
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Black)).assertTextEquals("Black")

        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).performClick()

        assertEquals(listOf(settings.copy(theme = ReaderTheme.Light)), changes)
    }

    @Test
    fun typefaceCapsulesShowOneSelectedAndTappingOneChangesOnlyTheTypeface() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings()
        host(settings, changes)

        node(ReaderSettingsSheetTags.typeface(ReaderTypeface.Original)).assertIsSelected()
        listOf("Editorial", "Classic", "Modern", "Sans", "Mono").forEach { label ->
            val face = ReaderTypeface.entries.first { it.name == label }
            node(ReaderSettingsSheetTags.typeface(face)).assertTextEquals(label)
            node(ReaderSettingsSheetTags.typeface(face)).assertIsNotSelected()
        }

        node(ReaderSettingsSheetTags.typeface(ReaderTypeface.Classic)).performClick()

        assertEquals(listOf(settings.copy(typeface = ReaderTypeface.Classic)), changes)
    }

    @Test
    fun sizeStepperStepsByOneAndDisablesAtItsBounds() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = host(ReaderSettings(fontSize = 19), changes)

        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("19")
        node(ReaderSettingsSheetTags.SIZE_INCREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("20")
        node(ReaderSettingsSheetTags.SIZE_DECREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_DECREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("18")
        assertEquals(listOf(20, 19, 18), changes.map { it.fontSize })

        rule.runOnIdle { settings.value = ReaderSettings(fontSize = 34) }
        node(ReaderSettingsSheetTags.SIZE_INCREASE).assertIsNotEnabled()
        node(ReaderSettingsSheetTags.SIZE_DECREASE).assertIsEnabled()
        val before = changes.size
        node(ReaderSettingsSheetTags.SIZE_INCREASE).performClick()
        assertEquals(before, changes.size)

        rule.runOnIdle { settings.value = ReaderSettings(fontSize = 12) }
        node(ReaderSettingsSheetTags.SIZE_DECREASE).assertIsNotEnabled()
        node(ReaderSettingsSheetTags.SIZE_INCREASE).assertIsEnabled()
        node(ReaderSettingsSheetTags.SIZE_DECREASE).performClick()
        assertEquals(before, changes.size)
    }

    @Test
    fun lineHeightStepperShowsOneDecimalStepsExactlyAndDisablesAtItsBounds() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = host(ReaderSettings(lineHeight = 1.6), changes)

        node(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE).assertTextEquals("1.6")
        node(ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE).performClick()

        assertEquals(1.7, changes.single().lineHeight, 0.0)
        node(ReaderSettingsSheetTags.LINE_HEIGHT_VALUE).assertTextEquals("1.7")

        rule.runOnIdle { settings.value = ReaderSettings(lineHeight = 1.2) }
        node(ReaderSettingsSheetTags.LINE_HEIGHT_DECREASE).assertIsNotEnabled()
        node(ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE).assertIsEnabled()

        rule.runOnIdle { settings.value = ReaderSettings(lineHeight = 2.2) }
        node(ReaderSettingsSheetTags.LINE_HEIGHT_INCREASE).assertIsNotEnabled()
        node(ReaderSettingsSheetTags.LINE_HEIGHT_DECREASE).assertIsEnabled()
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
    fun marginsSelectorShowsTheChosenMarginAndTappingOneChangesOnlyMargins() {
        val changes = mutableListOf<ReaderSettings>()
        val settings = ReaderSettings(margins = ReaderMargins.Normal)
        host(settings, changes)

        node(ReaderSettingsSheetTags.margins(ReaderMargins.Normal)).assertIsSelected()
        node(ReaderSettingsSheetTags.margins(ReaderMargins.Narrow)).assertIsNotSelected()
        node(ReaderSettingsSheetTags.margins(ReaderMargins.Wide)).assertIsNotSelected()

        node(ReaderSettingsSheetTags.margins(ReaderMargins.Wide)).performClick()

        assertEquals(listOf(settings.copy(margins = ReaderMargins.Wide)), changes)
        node(ReaderSettingsSheetTags.margins(ReaderMargins.Wide)).assertIsSelected()
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
        host(ReaderSettings(theme = ReaderTheme.Dark, fontSize = 19))

        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).performClick()
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Light)).assertIsSelected()
        node(ReaderSettingsSheetTags.swatch(ReaderTheme.Dark)).assertIsNotSelected()

        node(ReaderSettingsSheetTags.SIZE_INCREASE).performClick()
        node(ReaderSettingsSheetTags.SIZE_VALUE).assertTextEquals("20")
    }

    @Test
    fun everyControlIsReachableInTheSheetsScrollingContent() {
        host()

        rule.onNodeWithTag(ReaderSettingsSheetTags.SHEET).assert(hasScrollAction())
        node(ReaderSettingsSheetTags.JUSTIFY).performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("LAYOUT").fetchSemanticsNode()
        rule.onNodeWithText("PAGE").fetchSemanticsNode()
        rule.onNodeWithText("TYPE").fetchSemanticsNode()
    }

    @Test
    fun opensFromTheMenuDoneClosesItAndBackClosesIt() {
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

        rule.runOnIdle { uiState.value = uiState.value.withMenuRowChosen(ReaderMenuRow.Settings) }
        rule.onNodeWithTag(ReaderSettingsSheetTags.SHEET).assertIsDisplayed()
        Espresso.pressBack()
        rule.waitUntil(timeoutMillis = 5_000) { rule.onAllNodesWithTag(ReaderSettingsSheetTags.SHEET).fetchSemanticsNodes().isEmpty() }
        assertEquals(2, dismissed)
    }
}
