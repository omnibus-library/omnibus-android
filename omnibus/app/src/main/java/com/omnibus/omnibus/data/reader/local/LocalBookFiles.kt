package com.omnibus.omnibus.data.reader.local

import java.io.File

/** Finds a book's EPUB on disk, so a downloaded book is read without the network. */
fun interface LocalBookFiles {

    /** The [uuid] book's downloaded file, or null when it isn't on this device. */
    fun find(uuid: String): File?

    companion object {
        /** No downloads exist yet, so nothing is ever local. */
        val None = LocalBookFiles { null }
    }
}
