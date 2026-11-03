package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest

/** A plurals string for [count], formatted with it, as the app shows it. */
fun deletePlural(id: Int, count: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.getQuantityString(id, count, count)

/** The `Set` with [id] toggled. */
fun Set<Long>.flipped(id: Long): Set<Long> = if (id in this) this - id else this + id

fun deleteFile(
    id: Long,
    label: String? = null,
    format: String = "epub",
    filename: String = "file-$id.$format",
    sizeBytes: Long = 2048,
) = DeletableFile(id = id, format = format, filename = filename, label = label, sizeBytes = sizeBytes)

fun deleteCopy(id: Long, isbn: String? = null, note: String? = null) =
    DeletableCopy(id = id, isbn = isbn, note = note)

fun deleteManifest(
    files: List<DeletableFile> = emptyList(),
    copies: List<DeletableCopy> = emptyList(),
    impact: DeletionImpact = DeletionImpact(),
) = DeletionManifest(files = files, copies = copies, impact = impact)

/**
 * Counts what the sheet's callbacks received. [toggleFile], [toggleCopy] and [toggleAll] default to recording, and a test
 * can replace them to wire the picks to its own state.
 */
internal class DeleteTaps {
    val toggledFiles = mutableListOf<Long>()
    val toggledCopies = mutableListOf<Long>()
    var toggledAll = 0
    var continues = 0
    var backs = 0
    var confirms = 0
    var closes = 0
    var retries = 0

    fun callbacks(
        toggleFile: (Long) -> Unit = { toggledFiles += it },
        toggleCopy: (Long) -> Unit = { toggledCopies += it },
        toggleAll: () -> Unit = { toggledAll++ },
    ) = DeleteSheetCallbacks(
        onToggleFile = toggleFile,
        onToggleCopy = toggleCopy,
        onToggleAll = toggleAll,
        onContinue = { continues++ },
        onBack = { backs++ },
        onConfirm = { confirms++ },
        onClose = { closes++ },
        onRetry = { retries++ },
    )
}
