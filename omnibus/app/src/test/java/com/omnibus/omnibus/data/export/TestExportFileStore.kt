package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.local.ExportFileStore
import java.io.File

/**
 * [ExportFileStore] for tests. Folders are created under [root] and recorded in [folders], and [usable] is
 * the free space it reports.
 */
class TestExportFileStore(private val root: File, var usable: Long = Long.MAX_VALUE) : ExportFileStore {

    /** Every folder handed out, in order. */
    val folders = mutableListOf<File>()

    override fun newFolder(): File = File(root, "f${folders.size}").apply { mkdirs() }.also { folders += it }

    override fun usableBytes(): Long = usable
}
