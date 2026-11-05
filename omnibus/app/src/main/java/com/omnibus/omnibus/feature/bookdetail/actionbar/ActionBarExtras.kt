package com.omnibus.omnibus.feature.bookdetail.actionbar

import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.more.export.exportSize
import java.util.Locale

/**
 * A round button beside the main button, for a book with both an ebook and an audiobook. [Read] is the first button
 * when the main button is listening; [Listen] is the first button otherwise. [ImmersiveRead] is always the last.
 */
sealed interface RoundAction {

    /** Opens the reader. */
    data class Read(val destination: BookDetailDestination.Reader) : RoundAction

    /**
     * Opens the audiobook, through [target]. [enabled] is false while the audiobook cannot open offline.
     *
     * @property enabled Whether a tap does anything.
     * @property target Where a tap leads: the player, or the file chooser for a book with several audio files.
     */
    data class Listen(val enabled: Boolean, val target: ListenTarget) : RoundAction

    /**
     * Starts the audiobook in the background and opens the reader.
     *
     * @property reader The reader to open.
     * @property audioFileId The file to start, or null for the server's choice.
     * @property loadsAudio False when listening is unavailable: the reader opens without loading any audio.
     */
    data class ImmersiveRead(
        val reader: BookDetailDestination.Reader,
        val audioFileId: Long?,
        val loadsAudio: Boolean,
    ) : RoundAction
}

/** Where a [RoundAction.Listen] tap leads. */
sealed interface ListenTarget {

    /** Opens the player directly. */
    data class Open(val destination: BookDetailDestination.Player) : ListenTarget

    /** Opens the file chooser with [choices], in the order of the files' ordinals. */
    data class Choose(val choices: List<AudioFileChoice>) : ListenTarget
}

/**
 * One row of the file chooser.
 *
 * @property id The file's id, which the player opens.
 * @property name The file's label, or its filename when the label is blank.
 * @property format The file's format as the server sent it.
 * @property sizeBytes The file's size, or 0 when unknown.
 * @property continues Whether this file holds the saved listening position.
 */
data class AudioFileChoice(
    val id: Long,
    val name: String,
    val format: String,
    val sizeBytes: Long,
    val continues: Boolean,
)

/** The audio formats the server resolves for listening, as iOS `Book.audioFiles`. Compared lowercase. */
internal val SELECTABLE_AUDIO_FORMATS = setOf("m4b", "m4a", "mp3")

/** The book's files that can be listened to, in ordinal order. */
fun selectableAudioFiles(book: Book): List<BookFile> =
    book.bookFiles.filter { it.format.lowercase(Locale.ROOT) in SELECTABLE_AUDIO_FORMATS }.sortedBy { it.ordinal }

/** A file's name in the chooser: its label when it is not blank, otherwise its filename. */
fun audioFileName(file: BookFile): String = file.label?.takeIf { it.isNotBlank() } ?: file.filename

/** A file's detail line: its upper-cased format and its size, when known, joined with " · ". */
fun audioFileDetail(format: String, sizeBytes: Long): String =
    listOfNotNull(format.uppercase(Locale.ROOT), exportSize(sizeBytes)).joinToString(" · ")

/**
 * Where [RoundAction.Listen] leads for [book]. Two or more selectable files open the chooser, with the saved file
 * marked as continuing. Zero or one open the player on [savedAudioFileId], which null leaves to the server.
 */
fun listenTarget(book: Book, savedAudioFileId: Long?): ListenTarget {
    val files = selectableAudioFiles(book)
    return if (files.size >= 2) {
        ListenTarget.Choose(
            files.map { file ->
                AudioFileChoice(
                    id = file.id,
                    name = audioFileName(file),
                    format = file.format,
                    sizeBytes = file.sizeBytes,
                    continues = file.id == savedAudioFileId,
                )
            },
        )
    } else {
        ListenTarget.Open(BookDetailDestination.Player(book.uuid, savedAudioFileId))
    }
}

/**
 * The round buttons beside [primary] for [book], or none unless the book has both an ebook and an audiobook. The first
 * button is [RoundAction.Read] when the main button is listening, and [RoundAction.Listen] otherwise. [listenAvailable]
 * is false while the audiobook cannot open offline: Listen is then disabled, and Immersive read opens the reader only.
 */
fun roundActions(
    book: Book,
    primary: ActionBarModel.Primary,
    savedAudioFileId: Long?,
    listenAvailable: Boolean,
): List<RoundAction> {
    if (!(book.hasEbook && book.hasAudiobook)) return emptyList()
    val reader = BookDetailDestination.Reader(book.uuid)
    val first = if (primary.label.isListening) {
        RoundAction.Read(reader)
    } else {
        RoundAction.Listen(listenAvailable, listenTarget(book, savedAudioFileId))
    }
    return listOf(first, RoundAction.ImmersiveRead(reader, savedAudioFileId, loadsAudio = listenAvailable))
}
