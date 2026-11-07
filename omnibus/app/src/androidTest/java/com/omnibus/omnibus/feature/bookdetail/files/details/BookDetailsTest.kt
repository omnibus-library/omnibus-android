package com.omnibus.omnibus.feature.bookdetail.files.details

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

/** The Details list at the end of the Files tab: how values are copied and read out. */
class BookDetailsTest {

    @get:Rule
    val rule = createComposeRule()

    private val tags = BookDetailsTestTags

    private fun show(items: List<DetailItem>) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) { BookDetails(items, Modifier.fillMaxWidth()) }
        }
        rule.waitForIdle()
    }

    // TC-7 (AC3): a value cut on screen is still read out whole. BookDetailsScreenshotTest shows the cut.
    @Test
    fun tc7_aCutValueIsReadOutWhole() {
        val long = "Start_" + "x".repeat(300) + "_end.epub"
        show(listOf(DetailItem(DetailRow.File, long)))

        rule.onNodeWithTag(tags.row(DetailRow.File))
            .assertContentDescriptionEquals(detailText(R.string.book_detail_details_row_description, "File", long))
    }

    // TC-8 (AC3)
    @Test
    fun tc8_copyingFromACutValueStoresAllOfIt() {
        val clipboard = RecordingClipboard()

        runBlocking {
            WholeValueClipboard(clipboard, "Start_whole_end.epub")
                .setClipEntry(ClipEntry(ClipData.newPlainText(null, "Start_wh…ole_end.epub")))
        }

        assertEquals("Start_whole_end.epub", clipboard.stored?.clipData?.getItemAt(0)?.text?.toString())
    }

    /** A clipboard that keeps what it is given. */
    private class RecordingClipboard : Clipboard {
        var stored: ClipEntry? = null

        override suspend fun getClipEntry(): ClipEntry? = stored

        override suspend fun setClipEntry(clipEntry: ClipEntry?) {
            stored = clipEntry
        }

        override val nativeClipboard: ClipboardManager
            get() = InstrumentationRegistry.getInstrumentation().targetContext
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
}
