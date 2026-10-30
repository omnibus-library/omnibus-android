package com.omnibus.omnibus.data.reader

import java.io.InputStream

/** The outcome of opening a book's EPUB for the reader. */
sealed interface BookFileResult {

    /**
     * The book's bytes, ready to stream. The caller owns [stream] and must close it, which also
     * releases the underlying network connection.
     *
     * @property length The size in bytes, or -1 when the server didn't say.
     */
    data class Opened(val stream: InputStream, val length: Long) : BookFileResult

    /** A book that couldn't be opened. */
    sealed interface Failure : BookFileResult

    /** The server rejected the saved sign-in. */
    data object Unauthenticated : Failure

    /** The server has no file for this book. */
    data object NotFound : Failure

    /** No server address is saved. */
    data object NoServer : Failure

    /** The server couldn't be reached or answered unexpectedly. */
    data object Error : Failure
}
