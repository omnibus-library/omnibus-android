package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile

/** The largest file Kindle's email address accepts. Larger files are sent through Amazon's web uploader instead. */
internal const val KINDLE_EMAIL_MAX_BYTES = 50_000_000L

/** Amazon's web uploader, opened by the Kindle "Too large" row. */
internal const val KINDLE_WEB_UPLOAD_URL = "https://www.amazon.com/sendtokindle"

private const val EPUB = "epub"
private const val PDF = "pdf"
private const val KOBO_FORMAT = "EPUB"
private const val DEFAULT_AUDIO_EXTENSION = "m4b"
private val SENDABLE_AUDIO_FORMATS = setOf("m4b", "m4a", "mp3")

/** Whether the Kindle row can be sent, can be too large, or is blocked by a missing address or no connection. */
enum class KindleGate { Ready, Oversize, NoAddress, Offline }

/** Why a Kindle send failed. The copy for each reason is a string resource. */
enum class KindleFailure { Undelivered, Unconfirmed, NotConfigured, NoAddress, NotFound, Unreachable, Unknown }

/** Where the Kindle send is. */
sealed interface KindlePhase {

    /** No send has started in this opening of the sheet. */
    data object Idle : KindlePhase

    /** A send is queued and being polled. */
    data object Sending : KindlePhase

    /** The server confirmed the book was mailed. */
    data object Sent : KindlePhase

    /**
     * The send failed. [serverMessage], when non-null, is shown instead of [reason]'s sentence.
     */
    data class Failed(val reason: KindleFailure, val serverMessage: String? = null) : KindlePhase
}

/** The three downloads the save rows start. */
enum class ExportSave { Kobo, Ebook, Audio }

/** What the Kindle row shows. */
enum class KindleRowState { Ready, Sending, Sent, Failed, NoAddress, Oversize, Offline }

/**
 * The Kindle row of the sheet.
 *
 * @property sendsEpub Whether the send mails an EPUB, which sets the subtitle copy.
 * @property failure The failed phase, when the row is in [KindleRowState.Failed].
 */
data class KindleRow(
    val state: KindleRowState,
    val enabled: Boolean,
    val dimmed: Boolean,
    val sendsEpub: Boolean,
    val failure: KindlePhase.Failed? = null,
)

/**
 * A Kobo, ebook or audiobook save row.
 *
 * @property format The format the row names, or null when it names none.
 * @property sizeBytes The size of the file the row downloads, or 0 when unknown.
 * @property offline Whether the device is offline, which explains the disabled row.
 * @property busy Whether this row's download is running.
 * @property dimmed Whether the row is drawn at reduced opacity (offline, or another save is running).
 */
data class SaveRow(
    val kind: ExportSave,
    val format: String?,
    val sizeBytes: Long,
    val enabled: Boolean,
    val offline: Boolean,
    val busy: Boolean,
    val dimmed: Boolean,
)

/**
 * The rows the Export sheet shows. A null row is not shown.
 */
data class ExportSheetModel(
    val kindle: KindleRow?,
    val kobo: SaveRow?,
    val ebook: SaveRow?,
    val audio: SaveRow?,
) {
    /** Whether the "Send to a device" section shows. */
    val showsSendSection: Boolean get() = kindle != null || kobo != null

    /** Whether the "Save a copy" section shows. */
    val showsSaveSection: Boolean get() = ebook != null || audio != null
}

/** The file an ebook row downloads. */
data class EbookTarget(val format: String, val sizeBytes: Long, val fallbackExtension: String)

/** The file an audiobook row downloads. [format] is null when no sendable audio file is listed. */
data class AudioTarget(val format: String?, val sizeBytes: Long, val fallbackExtension: String)

/** Whether the book has a file in [name]'s format, from its formats or its files. Names compare case-insensitively. */
private fun isFormat(book: Book, name: String): Boolean =
    book.formats.any { it.equals(name, ignoreCase = true) } ||
        book.bookFiles.any { it.format.equals(name, ignoreCase = true) }

/** The lowest-ordinal file of [format] in the book, or null. */
private fun firstFile(book: Book, format: String): BookFile? =
    book.bookFiles.filter { it.format.equals(format, ignoreCase = true) }.minByOrNull { it.ordinal }

/** Whether the server can mail the book to a Kindle: it has an EPUB or a PDF. */
internal fun hasKindleSendable(book: Book): Boolean = isFormat(book, EPUB) || isFormat(book, PDF)

/** The file a Kindle send mails: the lowest-ordinal EPUB, else the lowest-ordinal PDF, or null. */
internal fun kindleFile(book: Book): BookFile? = firstFile(book, EPUB) ?: firstFile(book, PDF)

/**
 * Whether the More menu shows the Export row: the book has formats, and the user may download or the book can be sent
 * to a Kindle.
 */
internal fun showsExportRow(book: Book, user: CurrentUser?): Boolean =
    book.formats.isNotEmpty() && (user?.canDownload == true || hasKindleSendable(book))

/**
 * The Kindle gate, in the order iOS checks it: too large, then no address, then offline. Only a [KindleGate.Ready]
 * gate lets the send run.
 */
internal fun kindleGate(book: Book, kindleEmail: String?, isOnline: Boolean): KindleGate = when {
    (kindleFile(book)?.sizeBytes ?: 0L) > KINDLE_EMAIL_MAX_BYTES -> KindleGate.Oversize
    kindleEmail.isNullOrBlank() -> KindleGate.NoAddress
    !isOnline -> KindleGate.Offline
    else -> KindleGate.Ready
}

/** The file the ebook row downloads: the EPUB, else the PDF, or null when the book has neither. */
internal fun ebookTarget(book: Book): EbookTarget? = when {
    isFormat(book, EPUB) -> EbookTarget("EPUB", firstFile(book, EPUB)?.sizeBytes ?: 0L, EPUB)
    isFormat(book, PDF) -> EbookTarget("PDF", firstFile(book, PDF)?.sizeBytes ?: 0L, PDF)
    else -> null
}

/**
 * The file the audiobook row downloads: the lowest-ordinal m4b, m4a or mp3 file. Null unless the book has an
 * audiobook format.
 */
internal fun audioTarget(book: Book): AudioTarget? {
    if (!book.hasAudiobook) return null
    val file = book.bookFiles
        .filter { it.format.lowercase() in SENDABLE_AUDIO_FORMATS }
        .minByOrNull { it.ordinal }
    return AudioTarget(
        format = file?.format?.uppercase(),
        sizeBytes = file?.sizeBytes ?: 0L,
        fallbackExtension = file?.format?.lowercase() ?: DEFAULT_AUDIO_EXTENSION,
    )
}

/**
 * The whole Export sheet for one book, derived from the state the ViewModel holds. The Kindle row ignores [saving];
 * the save rows ignore [kindle].
 *
 * @param isOnline Whether the device is online. Offline, every network row is disabled and dimmed.
 * @param kindle The Kindle send's phase. Only shown when the gate is ready.
 * @param saving The save that is running, if any. The other save rows wait for it.
 */
internal fun exportSheet(
    book: Book,
    user: CurrentUser?,
    isOnline: Boolean,
    kindle: KindlePhase,
    saving: ExportSave?,
): ExportSheetModel {
    val canDownload = user?.canDownload == true
    val kindleRow = if (hasKindleSendable(book)) kindleRow(book, user, isOnline, kindle) else null
    val kobo = if (canDownload && isFormat(book, EPUB)) {
        saveRow(ExportSave.Kobo, KOBO_FORMAT, 0L, isOnline, saving)
    } else {
        null
    }
    val ebook = if (canDownload) {
        ebookTarget(book)?.let { saveRow(ExportSave.Ebook, it.format, it.sizeBytes, isOnline, saving) }
    } else {
        null
    }
    val audio = if (canDownload) {
        audioTarget(book)?.let { saveRow(ExportSave.Audio, it.format, it.sizeBytes, isOnline, saving) }
    } else {
        null
    }
    return ExportSheetModel(kindleRow, kobo, ebook, audio)
}

/** The Kindle row: the gate decides first, and only a ready gate shows the send phase. */
private fun kindleRow(book: Book, user: CurrentUser?, isOnline: Boolean, phase: KindlePhase): KindleRow {
    val sendsEpub = isFormat(book, EPUB)
    return when (kindleGate(book, user?.kindleEmail, isOnline)) {
        KindleGate.Oversize -> KindleRow(KindleRowState.Oversize, enabled = true, dimmed = false, sendsEpub = sendsEpub)
        KindleGate.NoAddress -> KindleRow(KindleRowState.NoAddress, enabled = true, dimmed = false, sendsEpub = sendsEpub)
        KindleGate.Offline -> KindleRow(KindleRowState.Offline, enabled = false, dimmed = true, sendsEpub = sendsEpub)
        KindleGate.Ready -> when (phase) {
            KindlePhase.Idle -> KindleRow(KindleRowState.Ready, enabled = true, dimmed = false, sendsEpub = sendsEpub)
            KindlePhase.Sending -> KindleRow(KindleRowState.Sending, enabled = false, dimmed = false, sendsEpub = sendsEpub)
            KindlePhase.Sent -> KindleRow(KindleRowState.Sent, enabled = false, dimmed = false, sendsEpub = sendsEpub)
            is KindlePhase.Failed -> KindleRow(
                KindleRowState.Failed,
                enabled = true,
                dimmed = false,
                sendsEpub = sendsEpub,
                failure = phase,
            )
        }
    }
}

/**
 * A save row. Offline it is disabled and dimmed; while any save runs, the other rows are disabled and dimmed and the
 * running row is not.
 */
private fun saveRow(kind: ExportSave, format: String?, sizeBytes: Long, isOnline: Boolean, saving: ExportSave?): SaveRow {
    val busy = saving == kind
    return SaveRow(
        kind = kind,
        format = format,
        sizeBytes = sizeBytes,
        enabled = isOnline && saving == null,
        offline = !isOnline,
        busy = busy,
        dimmed = !isOnline || (saving != null && !busy),
    )
}
