package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ExportSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val sending get() = detailText(R.string.book_detail_export_kindle_sending)
    private val settings get() = detailText(R.string.book_detail_export_kindle_settings)
    private val opens get() = detailText(R.string.book_detail_export_opens_share_sheet)

    // TC-60
    @Test
    fun issue118_tc60_kindleRowSendsOnlyWhenIdle() {
        val inputs = SheetInputs(modelOf(epubBook))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KINDLE).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.sends)

        rule.runOnIdle {
            inputs.model = modelOf(epubBook, kindle = KindlePhase.Sending)
            inputs.sending = true
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).performClick()
        rule.waitForIdle()

        assertEquals("a tap while sending is ignored", 1, taps.sends)
    }

    // TC-61
    @Test
    fun issue118_tc61_failedKindleRetries() {
        val taps = SheetTaps()
        rule.setSheet(SheetInputs(modelOf(epubBook, kindle = KindlePhase.Failed(KindleFailure.Unconfirmed))), taps)

        rule.onNodeWithTag(ExportTestTags.KINDLE).performClick()
        rule.waitForIdle()

        assertEquals(1, taps.sends)
    }

    // TC-62
    @Test
    fun issue118_tc62_noAddressAndOversizeOpenTheirActionsWhileOffline() {
        val inputs = SheetInputs(modelOf(epubBook, user = downloader.copy(kindleEmail = null), online = false))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KINDLE).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.settings)

        rule.runOnIdle { inputs.model = modelOf(oversizeBook, online = false) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).performClick()
        rule.waitForIdle()

        assertEquals(1, taps.opens)
    }

    // TC-64
    @Test
    fun issue118_tc64_koboRowSaves() {
        val taps = SheetTaps()
        rule.setSheet(SheetInputs(modelOf(epubBook)), taps)

        rule.onNodeWithTag(ExportTestTags.KOBO).performClick()
        rule.waitForIdle()

        assertEquals(listOf(ExportSave.Kobo), taps.saves)
    }

    // TC-65
    @Test
    fun issue118_tc65_downloadRowsSaveTheirKind() {
        val taps = SheetTaps()
        rule.setSheet(SheetInputs(modelOf(epubAndAudioBook)), taps)

        rule.onNodeWithTag(ExportTestTags.EBOOK).performClick()
        rule.onNodeWithTag(ExportTestTags.AUDIO).performScrollTo().performClick()
        rule.waitForIdle()

        assertEquals(listOf(ExportSave.Ebook, ExportSave.Audio), taps.saves)
    }

    // TC-69
    @Test
    fun issue118_tc69_closeWorksWhenIdleAndIsIgnoredWhileSending() {
        val inputs = SheetInputs(modelOf(epubBook))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.CLOSE).assertIsEnabled().performClick()
        rule.waitForIdle()
        assertEquals(1, taps.closes)

        rule.runOnIdle {
            inputs.model = modelOf(epubBook, kindle = KindlePhase.Sending)
            inputs.sending = true
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.CLOSE).assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertEquals(1, taps.closes)
    }

    // TC-70
    @Test
    fun issue118_tc70_backKeepsTheSheetWhileSendingAndClosesItWhenIdle() {
        val sendingNow = mutableStateOf(true)
        val taps = SheetTaps()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                val isSending = sendingNow.value
                ExportSheet(
                    model = modelOf(epubBook, kindle = if (isSending) KindlePhase.Sending else KindlePhase.Idle),
                    saveError = null,
                    sending = isSending,
                    accentInk = sheetAccent,
                    onSend = {},
                    onSave = {},
                    onSettings = {},
                    onOpen = {},
                    onClose = { taps.closes++ },
                )
            }
        }

        rule.onNodeWithTag(ExportTestTags.SHEET).assertIsDisplayed()
        rule.pressBackKey()
        rule.onNodeWithTag(ExportTestTags.SHEET).assertIsDisplayed()
        assertEquals("Back is ignored while sending", 0, taps.closes)

        rule.runOnIdle { sendingNow.value = false }
        rule.waitForIdle()
        rule.pressBackKey()
        // The sheet animates away on Back and reports the dismissal afterwards.
        rule.waitUntil(5_000) { taps.closes >= 1 }
        rule.waitForIdle()
        assertEquals("Back closes the idle sheet once", 1, taps.closes)
    }
}
