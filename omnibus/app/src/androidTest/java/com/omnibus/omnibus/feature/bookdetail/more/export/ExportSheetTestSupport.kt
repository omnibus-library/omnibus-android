package com.omnibus.omnibus.feature.bookdetail.more.export

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.ui.theme.OmnibusTheme

/** A signed-in user who may download and has a Kindle address. */
internal val downloader = CurrentUser(
    id = 1,
    canEdit = false,
    canUpload = false,
    hiddenFormats = emptySet(),
    canDownload = true,
    kindleEmail = "reader@kindle.com",
)

/** A file of [format] at [ordinal] in a book, of [sizeBytes]. */
internal fun bookFile(format: String, sizeBytes: Long, ordinal: Int = 1): BookFile =
    BookFile(ordinal.toLong(), format, "book-$ordinal.$format", ordinal, null, sizeBytes, null)

/** A test book whose formats are its files' formats. */
internal fun bookWith(vararg files: BookFile): Book =
    testDetailBook(formats = files.map { it.format }.distinct()).copy(bookFiles = files.toList())

internal val epubBook: Book = bookWith(bookFile("epub", 412_000))

internal val oversizeBook: Book = bookWith(bookFile("epub", 60_000_000))
internal val epubAndAudioBook: Book = bookWith(bookFile("epub", 412_000), bookFile("m4b", 138_000_000, ordinal = 2))

/** The end-label colour the sheet uses for these tests. */
internal val sheetAccent: Color = exportAccentInk(epubBook)

/** The sheet's model for [book], from the real pure function. */
internal fun modelOf(
    book: Book,
    user: CurrentUser? = downloader,
    online: Boolean = true,
    kindle: KindlePhase = KindlePhase.Idle,
    saving: ExportSave? = null,
): ExportSheetModel = exportSheet(book, user, online, kindle, saving)

/** What the sheet shows. A test changes these between frames. */
internal class SheetInputs(
    model: ExportSheetModel,
    saveError: ExportSaveError? = null,
    sending: Boolean = false,
) {
    var model by mutableStateOf(model)
    var saveError by mutableStateOf(saveError)
    var sending by mutableStateOf(sending)
}

/** The callbacks the sheet made, counted in order. */
internal class SheetTaps {
    var sends = 0
    @Volatile var closes = 0
    var settings = 0
    var opens = 0
    val saves = mutableListOf<ExportSave>()
}

/** Sets [ExportSheetContent] in dark theme, drawn from [inputs] and reporting taps to [taps]. */
internal fun ComposeContentTestRule.setSheet(inputs: SheetInputs, taps: SheetTaps = SheetTaps()) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            ExportSheetContent(
                model = inputs.model,
                saveError = inputs.saveError,
                sending = inputs.sending,
                accentInk = sheetAccent,
                onSend = { taps.sends++ },
                onSave = { taps.saves += it },
                onSettings = { taps.settings++ },
                onOpen = { taps.opens++ },
                onClose = { taps.closes++ },
            )
        }
    }
}
