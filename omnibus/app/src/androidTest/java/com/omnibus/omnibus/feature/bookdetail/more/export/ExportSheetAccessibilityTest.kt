package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import org.junit.Rule
import org.junit.Test

class ExportSheetAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val button = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val politeLive = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    private val title get() = detailText(R.string.book_detail_export_title)
    private val sendSection get() = detailText(R.string.book_detail_export_send_section)
    private val saveSection get() = detailText(R.string.book_detail_export_save_section)
    private val close get() = detailText(R.string.book_detail_export_close)
    private val sendToKindle get() = detailText(R.string.book_detail_export_kindle)
    private val emailsEpub get() = detailText(R.string.book_detail_export_kindle_sends_epub)
    private val send get() = detailText(R.string.book_detail_export_kindle_send)
    private val sendToKobo get() = detailText(R.string.book_detail_export_kobo)
    private val koboMeta get() = detailText(R.string.book_detail_export_kobo_meta)
    private val save get() = detailText(R.string.book_detail_export_save)
    private val saving get() = detailText(R.string.book_detail_export_saving)
    private val opens get() = detailText(R.string.book_detail_export_opens_share_sheet)
    private val downloadAudiobook get() = detailText(R.string.book_detail_export_download_audiobook)
    private val downloadEpub get() = detailText(R.string.book_detail_export_download_format, "EPUB")

    // TC-71
    @Test
    fun issue118_tc71_eachRowIsOneButtonWhoseTextIsItsTitleSubtitleAndEndLabel() {
        rule.setSheet(SheetInputs(modelOf(epubAndAudioBook)))

        rule.onNodeWithTag(ExportTestTags.KINDLE)
            .assert(button)
            .assertTextEquals(sendToKindle, emailsEpub, send)
        rule.onNodeWithTag(ExportTestTags.KOBO)
            .assert(button)
            .assertTextEquals(sendToKobo, koboMeta, save)
        rule.onNodeWithTag(ExportTestTags.EBOOK)
            .assert(button)
            .assertTextEquals(downloadEpub, "412 KB · $opens")
        rule.onNodeWithTag(ExportTestTags.AUDIO)
            .performScrollTo()
            .assert(button)
            .assertTextEquals(downloadAudiobook, "M4B · 138 MB · $opens")
    }

    // TC-72
    @Test
    fun issue118_tc72_theBusyRowSpeaksItsState() {
        rule.setSheet(SheetInputs(modelOf(epubAndAudioBook, saving = ExportSave.Ebook)))

        rule.onNodeWithTag(ExportTestTags.EBOOK)
            .assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, saving))
    }

    // TC-73
    @Test
    fun issue118_tc73_theKindleRowAndTheErrorArePoliteLiveRegions() {
        rule.setSheet(SheetInputs(modelOf(epubBook), saveError = ExportSaveError.Failed))

        rule.onNodeWithTag(ExportTestTags.KINDLE).assert(politeLive)
        rule.onNodeWithTag(ExportTestTags.ERROR).assert(politeLive)
    }

    // TC-74
    @Test
    fun issue118_tc74_theTitleAndSectionsAreHeadingsAndCloseIsLabelledAndClickable() {
        rule.setSheet(SheetInputs(modelOf(epubAndAudioBook)))

        rule.onNodeWithTag(ExportTestTags.TITLE).assert(heading).assertTextEquals(title)
        rule.onNodeWithTag(ExportTestTags.SEND_SECTION).assert(heading).assertTextEquals(sendSection)
        rule.onNodeWithTag(ExportTestTags.SAVE_SECTION).assert(heading).assertTextEquals(saveSection)
        rule.onNodeWithTag(ExportTestTags.CLOSE)
            .assert(hasContentDescription(close))
            .assert(button)
            .assertHasClickAction()
    }
}
