package com.omnibus.omnibus.data.export

import java.io.File

/** Kindle sends and book downloads for the Export sheet. Each call is one user action, answered with a result rather than an exception. */
interface ExportRepository {

    /** Queues a send of the book to the user's Kindle, then polls it to an outcome. Never queued offline and never retried. */
    suspend fun sendToKindle(bookUuid: String): KindleSendResult

    /** Downloads the KEPUB, or the plain EPUB when the server falls back to it. */
    suspend fun saveKepub(bookUuid: String, title: String): ExportSaveResult

    /** Downloads the book's EPUB, else its PDF. [fallbackExtension] names the file when the server names none. */
    suspend fun saveEbook(bookUuid: String, title: String, fallbackExtension: String, expectedBytes: Long): ExportSaveResult

    /** Downloads every part of the audiobook, in manifest order. */
    suspend fun saveAudiobook(bookUuid: String, title: String, fallbackExtension: String, expectedBytes: Long): ExportSaveResult
}

/** The outcome of a Kindle send. */
sealed interface KindleSendResult {
    /** The worker mailed the book. */
    data object Sent : KindleSendResult

    /** The worker reported the send failed. [message] is its text, or null when it sent none. */
    data class Rejected(val message: String?) : KindleSendResult

    /** The server could not confirm the send: the status was unknown, polling ran out, or the status request failed. */
    data object Unconfirmed : KindleSendResult

    /** The user has no Kindle address on the server. */
    data object NoAddress : KindleSendResult

    /** Email delivery is not configured on the server. */
    data object NotConfigured : KindleSendResult

    /** The book is not on the server. */
    data object NotFound : KindleSendResult

    /** The server could not be reached when the send was queued. */
    data object Unreachable : KindleSendResult

    /** Any other answer, including a body that did not decode. */
    data object Failed : KindleSendResult

    /** No server is saved. */
    data object NoServer : KindleSendResult
}

/** A downloaded file and the MIME type to share it with. */
data class ExportedFile(val file: File, val mimeType: String)

/** The outcome of a download. */
sealed interface ExportSaveResult {
    /** The files are in the export folder, in the order they were downloaded. */
    data class Saved(val files: List<ExportedFile>) : ExportSaveResult

    /** The account may not download books. */
    data object Forbidden : ExportSaveResult

    /** The file is not on the server. */
    data object NotFound : ExportSaveResult

    /** The server could not be reached, or the connection dropped. */
    data object Unreachable : ExportSaveResult

    /** The device does not have room for the file. */
    data object NoSpace : ExportSaveResult

    /** Any other answer. */
    data object Failed : ExportSaveResult

    /** No server is saved. */
    data object NoServer : ExportSaveResult
}
