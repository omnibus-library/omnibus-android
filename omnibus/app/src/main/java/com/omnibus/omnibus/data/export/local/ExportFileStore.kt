package com.omnibus.omnibus.data.export.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject

/** Where export downloads are written, and how much room is left for them. */
interface ExportFileStore {

    /** A new, empty folder for one export. Folders older than 24 h are removed first. */
    fun newFolder(): File

    /** The bytes free on the volume that holds the export folders. */
    fun usableBytes(): Long
}

/**
 * [ExportFileStore] under the app's cache directory, so Android may reclaim it. [nowMillis] is injectable
 * so the age rule can be tested.
 */
class DefaultExportFileStore internal constructor(
    private val root: File,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : ExportFileStore {

    @Inject
    constructor(@ApplicationContext context: Context) : this(File(context.cacheDir, ROOT_NAME))

    override fun newFolder(): File {
        root.mkdirs()
        purgeStale()
        return File(root, UUID.randomUUID().toString()).apply { mkdirs() }
    }

    override fun usableBytes(): Long {
        root.mkdirs()
        return root.usableSpace
    }

    /** Removes the children of the root that were last modified more than [MAX_AGE_MILLIS] ago. Errors are ignored. */
    private fun purgeStale() {
        runCatching {
            root.listFiles().orEmpty()
                .filter { nowMillis() - it.lastModified() > MAX_AGE_MILLIS }
                .forEach { it.deleteRecursively() }
        }
    }

    internal companion object {
        const val ROOT_NAME = "book-exports"
        const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    }
}
