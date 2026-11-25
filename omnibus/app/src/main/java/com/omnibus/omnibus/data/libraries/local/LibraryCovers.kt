package com.omnibus.omnibus.data.libraries.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where each library's covers are cached (C9): `files/covers/<libraryId>/`. The covers root is the one
 * directory the backup rules exclude, so every library's covers are excluded with it.
 */
@Singleton
class LibraryCovers internal constructor(filesDir: File) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.filesDir)

    /** The covers root, `files/covers`, whose subdirectories are the libraries. */
    val root: File = File(filesDir, ROOT)

    /** The covers directory of [libraryId]. It may not exist yet. */
    fun directory(libraryId: String): File = File(root, libraryId)

    /**
     * Moves the loose cover files at the top of [root], the cache of the builds before libraries, into the
     * covers directory of [libraryId]. Subdirectories are other libraries and are left alone.
     */
    fun moveLooseFilesInto(libraryId: String) {
        val target = directory(libraryId).apply { mkdirs() }
        looseFiles().forEach { file ->
            val destination = File(target, file.name)
            if (!file.renameTo(destination)) {
                file.copyTo(destination, overwrite = true)
                file.delete()
            }
        }
    }

    /** Deletes the loose cover files at the top of [root]. */
    fun deleteLooseFiles() {
        looseFiles().forEach { it.delete() }
    }

    /** Deletes the covers of [libraryId]. */
    fun delete(libraryId: String) {
        directory(libraryId).deleteRecursively()
    }

    private fun looseFiles(): List<File> = root.listFiles()?.filter { it.isFile }.orEmpty()

    companion object {
        /** The cache cap of each library's covers. */
        const val MAX_BYTES = 256L * 1024 * 1024

        private const val ROOT = "covers"
    }
}
