package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExportSheetTest {

    @get:Rule
    val rule = createComposeRule()

    private val title get() = detailText(R.string.book_detail_export_title)
    private val sendSection get() = detailText(R.string.book_detail_export_send_section)
    private val saveSection get() = detailText(R.string.book_detail_export_save_section)
    private val sendToKindle get() = detailText(R.string.book_detail_export_kindle)
    private val emailsEpub get() = detailText(R.string.book_detail_export_kindle_sends_epub)
    private val emailsBook get() = detailText(R.string.book_detail_export_kindle_sends_book)
    private val send get() = detailText(R.string.book_detail_export_kindle_send)
    private val sending get() = detailText(R.string.book_detail_export_kindle_sending)
    private val sentMeta get() = detailText(R.string.book_detail_export_kindle_sent_meta)
    private val sent get() = detailText(R.string.book_detail_export_kindle_sent)
    private val noAddress get() = detailText(R.string.book_detail_export_kindle_no_address)
    private val settings get() = detailText(R.string.book_detail_export_kindle_settings)
    private val oversize get() = detailText(R.string.book_detail_export_kindle_oversize)
    private val open get() = detailText(R.string.book_detail_export_kindle_open)
    private val retry get() = detailText(R.string.book_detail_export_kindle_retry)
    private val undelivered get() = detailText(R.string.book_detail_export_kindle_failed_undelivered)
    private val unconfirmed get() = detailText(R.string.book_detail_export_kindle_failed_unconfirmed)
    private val needsConnection get() = detailText(R.string.book_detail_export_needs_connection)
    private val sendToKobo get() = detailText(R.string.book_detail_export_kobo)
    private val koboMeta get() = detailText(R.string.book_detail_export_kobo_meta)
    private val save get() = detailText(R.string.book_detail_export_save)
    private val saving get() = detailText(R.string.book_detail_export_saving)
    private val opens get() = detailText(R.string.book_detail_export_opens_share_sheet)
    private val downloadAudiobook get() = detailText(R.string.book_detail_export_download_audiobook)

    // TC-59
    @Test
    fun issue118_tc59_sectionsAndRowsAppearInOrderAndOnlyWhereTheBookHasThem() {
        val inputs = SheetInputs(modelOf(epubAndAudioBook))
        rule.setSheet(inputs)

        rule.onNodeWithTag(ExportTestTags.TITLE).assertTextEquals(title)
        rule.onNodeWithTag(ExportTestTags.SEND_SECTION).assertTextEquals(sendSection)
        rule.onNodeWithTag(ExportTestTags.SAVE_SECTION).performScrollTo().assertTextEquals(saveSection)
        val tops = listOf(ExportTestTags.KINDLE, ExportTestTags.KOBO, ExportTestTags.EBOOK, ExportTestTags.AUDIO)
            .map { rule.onNodeWithTag(it).bounds().top }
        assertTrue("rows run top to bottom: $tops", tops.zipWithNext().all { (above, below) -> above < below })

        rule.runOnIdle { inputs.model = modelOf(epubBook, user = kindleOnlyUser) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertIsDisplayed()
        rule.onAllNodesWithTag(ExportTestTags.SAVE_SECTION).assertCountEquals(0)
        rule.onAllNodesWithTag(ExportTestTags.KOBO).assertCountEquals(0)

        rule.runOnIdle { inputs.model = modelOf(audioBook) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.AUDIO).performScrollTo().assertIsDisplayed()
        rule.onAllNodesWithTag(ExportTestTags.SEND_SECTION).assertCountEquals(0)
        rule.onAllNodesWithTag(ExportTestTags.KINDLE).assertCountEquals(0)
    }

    // TC-60
    @Test
    fun issue118_tc60_kindleRowShowsEachPhaseAndSendsOnlyWhenIdle() {
        val inputs = SheetInputs(modelOf(epubBook))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, emailsEpub, send).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.sends)

        rule.runOnIdle { inputs.model = modelOf(pdfBook) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, emailsBook, send)

        rule.runOnIdle {
            inputs.model = modelOf(epubBook, kindle = KindlePhase.Sending)
            inputs.sending = true
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, emailsEpub, sending)
            .assertIsNotEnabled()
            .performClick()
        rule.waitForIdle()
        assertEquals("a tap while sending is ignored", 1, taps.sends)

        rule.runOnIdle {
            inputs.model = modelOf(epubBook, kindle = KindlePhase.Sent)
            inputs.sending = false
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, sentMeta, sent).assertIsNotEnabled()
    }

    // TC-61
    @Test
    fun issue118_tc61_failedKindleShowsItsSentenceOrTheServerMessageAndRetries() {
        val inputs = SheetInputs(modelOf(epubBook, kindle = KindlePhase.Failed(KindleFailure.Unconfirmed)))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, unconfirmed, retry).performClick()
        rule.waitForIdle()
        assertEquals(1, taps.sends)

        rule.runOnIdle {
            inputs.model = modelOf(epubBook, kindle = KindlePhase.Failed(KindleFailure.Undelivered, "Mailbox rejected"))
        }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, "Mailbox rejected", retry)
        rule.onAllNodesWithText(undelivered, useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-62
    @Test
    fun issue118_tc62_noAddressAndOversizeOpenTheirActionsWhileOffline() {
        val inputs = SheetInputs(modelOf(epubBook, user = downloader.copy(kindleEmail = null), online = false))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KINDLE)
            .assertTextEquals(sendToKindle, noAddress, settings)
            .assertIsEnabled()
            .performClick()
        rule.waitForIdle()
        assertEquals(1, taps.settings)

        rule.runOnIdle { inputs.model = modelOf(oversizeBook, online = false) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE)
            .assertTextEquals(sendToKindle, oversize, open)
            .assertIsEnabled()
            .performClick()
        rule.waitForIdle()
        assertEquals(1, taps.opens)
    }

    // TC-63
    @Test
    fun issue118_tc63_offlineKindleRowSaysNeedsAConnectionAndHasNoSend() {
        rule.setSheet(SheetInputs(modelOf(epubBook, online = false)))

        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, needsConnection).assertIsNotEnabled()
        rule.onAllNodesWithText(send, useUnmergedTree = true).assertCountEquals(0)
    }

    // TC-64
    @Test
    fun issue118_tc64_koboRowSavesWhenIdleAndSaysSavingWhileBusy() {
        val inputs = SheetInputs(modelOf(epubBook))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KOBO).assertTextEquals(sendToKobo, koboMeta, save).performClick()
        rule.waitForIdle()
        assertEquals(listOf(ExportSave.Kobo), taps.saves)

        rule.runOnIdle { inputs.model = modelOf(epubBook, saving = ExportSave.Kobo) }
        rule.waitForIdle()
        // The busy end label is cleared from semantics; its state is the row's state description.
        rule.onNodeWithTag(ExportTestTags.KOBO)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, saving))
        rule.onAllNodesWithText(save, useUnmergedTree = true).assertCountEquals(0)
        assertEquals(listOf(ExportSave.Kobo), taps.saves)
    }

    // TC-65
    @Test
    fun issue118_tc65_downloadRowsShowFormatSizeAndOpensCopySaveWhenTappedAndSpinWhileBusy() {
        val inputs = SheetInputs(modelOf(epubAndAudioBook))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.EBOOK)
            .assertTextEquals(detailText(R.string.book_detail_export_download_format, "EPUB"), "412 KB · $opens")
            .performClick()
        rule.waitForIdle()
        assertEquals(listOf(ExportSave.Ebook), taps.saves)

        rule.runOnIdle { inputs.model = modelOf(bookWith(bookFile("pdf", 0))) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.EBOOK)
            .assertTextEquals(detailText(R.string.book_detail_export_download_format, "PDF"), opens)

        rule.runOnIdle { inputs.model = modelOf(audioBook) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.AUDIO)
            .assertTextEquals(downloadAudiobook, "M4B · 138 MB · $opens")
            .performClick()
        rule.waitForIdle()
        assertEquals(listOf(ExportSave.Ebook, ExportSave.Audio), taps.saves)

        rule.runOnIdle { inputs.model = modelOf(epubAndAudioBook, saving = ExportSave.Ebook) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.PROGRESS, useUnmergedTree = true).assertIsDisplayed()
        rule.onAllNodes(
            hasTestTag(ExportTestTags.SHARE_ICON) and hasAnyAncestor(hasTestTag(ExportTestTags.EBOOK)),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }

    // TC-66
    @Test
    fun issue118_tc66_whileAnEbookSavesTheOtherSaveRowsAreDisabledButKindleStaysOn() {
        val inputs = SheetInputs(modelOf(epubAndAudioBook, saving = ExportSave.Ebook))
        val taps = SheetTaps()
        rule.setSheet(inputs, taps)

        rule.onNodeWithTag(ExportTestTags.KOBO).assertIsNotEnabled().performClick()
        rule.onNodeWithTag(ExportTestTags.AUDIO).performScrollTo().assertIsNotEnabled().performClick()
        rule.waitForIdle()
        assertEquals(emptyList<ExportSave>(), taps.saves)
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertIsEnabled()
    }

    // TC-67
    @Test
    fun issue118_tc67_offlineDisablesTheRowsAndTheyReturnWhenOnline() {
        val inputs = SheetInputs(modelOf(epubAndAudioBook, online = false))
        rule.setSheet(inputs)

        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, needsConnection).assertIsNotEnabled()
        rule.onNodeWithTag(ExportTestTags.KOBO).assertTextEquals(sendToKobo, needsConnection).assertIsNotEnabled()
        rule.onNodeWithTag(ExportTestTags.EBOOK)
            .assertTextEquals(detailText(R.string.book_detail_export_download_format, "EPUB"), needsConnection)
            .assertIsNotEnabled()
        rule.onNodeWithTag(ExportTestTags.AUDIO).performScrollTo()
            .assertTextEquals(downloadAudiobook, needsConnection)
            .assertIsNotEnabled()

        rule.runOnIdle { inputs.model = modelOf(epubAndAudioBook) }
        rule.waitForIdle()
        rule.onNodeWithTag(ExportTestTags.KINDLE).assertTextEquals(sendToKindle, emailsEpub, send).assertIsEnabled()
        rule.onNodeWithTag(ExportTestTags.KOBO).assertTextEquals(sendToKobo, koboMeta, save).assertIsEnabled()
        rule.onNodeWithTag(ExportTestTags.EBOOK)
            .assertTextEquals(detailText(R.string.book_detail_export_download_format, "EPUB"), "412 KB · $opens")
            .assertIsEnabled()
        rule.onNodeWithTag(ExportTestTags.AUDIO).performScrollTo()
            .assertTextEquals(downloadAudiobook, "M4B · 138 MB · $opens")
            .assertIsEnabled()
    }

    // TC-68
    @Test
    fun issue118_tc68_eachSaveErrorShowsItsSentenceUntilCleared() {
        val inputs = SheetInputs(modelOf(epubBook))
        rule.setSheet(inputs)
        val sentences = listOf(
            ExportSaveError.Forbidden to R.string.book_detail_export_error_forbidden,
            ExportSaveError.NotFound to R.string.book_detail_export_error_not_found,
            ExportSaveError.Unreachable to R.string.book_detail_export_error_unreachable,
            ExportSaveError.NoSpace to R.string.book_detail_export_error_no_space,
            ExportSaveError.Failed to R.string.book_detail_export_error_failed,
        )

        for ((error, sentence) in sentences) {
            rule.runOnIdle { inputs.saveError = error }
            rule.waitForIdle()
            rule.onNodeWithTag(ExportTestTags.ERROR).assertTextEquals(detailText(sentence))
        }

        rule.runOnIdle { inputs.saveError = null }
        rule.waitForIdle()
        rule.onAllNodesWithTag(ExportTestTags.ERROR).assertCountEquals(0)
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
