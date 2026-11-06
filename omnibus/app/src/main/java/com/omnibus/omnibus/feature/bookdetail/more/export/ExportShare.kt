package com.omnibus.omnibus.feature.bookdetail.more.export

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.omnibus.omnibus.data.export.ExportedFile
import java.io.File

/** The export FileProvider's authority suffix; the full authority is `<applicationId>.bookexport`. */
private const val AUTHORITY_SUFFIX = ".bookexport"

/**
 * Builds the share chooser for downloaded [files] (#118). One file goes as `ACTION_SEND`, several as
 * `ACTION_SEND_MULTIPLE`; each file is a `content://` URI from the export FileProvider with a read grant. The chooser
 * carries `FLAG_ACTIVITY_NEW_TASK` when [context] is not an Activity.
 *
 * @throws IllegalArgumentException when [files] is empty, or a file is outside the provider's shared folder.
 */
internal fun exportChooserIntent(context: Context, files: List<ExportedFile>): Intent {
    require(files.isNotEmpty()) { "nothing to share" }
    val uris = files.map { exportUri(context, it.file) }
    val inner = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND)
            .setType(files.single().mimeType)
            .putExtra(Intent.EXTRA_STREAM, uris.single())
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType(sharedMimeType(files))
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
    }
    inner.clipData = if (uris.size == 1) {
        ClipData.newRawUri(files.single().file.name, uris.single())
    } else {
        clipDataOf(files, uris)
    }
    inner.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val chooser = Intent.createChooser(inner, null)
    if (context.findActivity() == null) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return chooser
}

/** The `content://` URI the export FileProvider serves [file] at. [file] must be under the export folders. */
internal fun exportUri(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)

/** The one MIME type all [files] share; otherwise the audio wildcard for audio only, else the any-type wildcard. */
private fun sharedMimeType(files: List<ExportedFile>): String {
    val mimes = files.map { it.mimeType }.distinct()
    return when {
        mimes.size == 1 -> mimes.single()
        files.all { it.mimeType.startsWith("audio/") } -> "audio/*"
        else -> "*/*"
    }
}

/** A clip listing every URI, so the receiving app is granted each one. */
private fun clipDataOf(files: List<ExportedFile>, uris: List<Uri>): ClipData {
    val clip = ClipData.newRawUri(files.first().file.name, uris.first())
    uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
    return clip
}

/** The Activity this context belongs to, or null for an application context. */
private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return context as? Activity
}
