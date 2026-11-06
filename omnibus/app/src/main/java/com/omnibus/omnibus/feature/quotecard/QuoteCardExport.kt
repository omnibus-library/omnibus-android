package com.omnibus.omnibus.feature.quotecard

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Constraints
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.export.ExportedFile
import com.omnibus.omnibus.feature.bookdetail.more.export.exportChooserIntent
import com.omnibus.omnibus.feature.bookdetail.more.export.exportUri
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders a [QuoteCard] at export size without showing it, and reads the picture back. The card is laid out at fixed
 * pixel constraints, not dp, so 1080 never rounds to 1079, and its draw is recorded into a layer that is never drawn.
 */
class QuoteCardExporter internal constructor(private val layer: GraphicsLayer) {

    /**
     * Composes [card] at [exportSize] for [shape], zero-sized and invisible in the layout. [card] must pass its modifier
     * to the [QuoteCard] it draws.
     */
    @Composable
    fun Offscreen(shape: QuoteShape, card: @Composable (Modifier) -> Unit) {
        val size = exportSize(shape)
        Layout(
            modifier = Modifier.clearAndSetSemantics {},
            content = {
                card(Modifier.drawWithContent { layer.record(size = size) { this@drawWithContent.drawContent() } })
            },
        ) { measurables, _ ->
            val placeable = measurables.single().measure(Constraints.fixed(size.width, size.height))
            layout(0, 0) { placeable.place(0, 0) }
        }
    }

    /** The card as last drawn, as PNG bytes. The PNG is encoded off the main thread. */
    suspend fun capture(): ByteArray {
        val bitmap = layer.toImageBitmap().asAndroidBitmap()
        return withContext(Dispatchers.Default) {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        }
    }
}

/** A [QuoteCardExporter] that lives as long as the composition. */
@Composable
fun rememberQuoteCardExporter(): QuoteCardExporter {
    val layer = rememberGraphicsLayer()
    return remember(layer) { QuoteCardExporter(layer) }
}

/** The chooser that shares the saved card [file] as an image. */
fun quoteCardShareIntent(context: Context, file: File): Intent =
    exportChooserIntent(context, listOf(ExportedFile(file, "image/png")))

/** A clip holding the saved card [file]'s content URI, for pasting the image elsewhere. */
fun quoteCardClip(context: Context, file: File): ClipData =
    ClipData.newUri(context.contentResolver, context.getString(R.string.quote_card_clip_label), exportUri(context, file))

/** The chooser that shares just the [passage], with its whitespace collapsed, as plain text. */
fun passageShareIntent(passage: String): Intent {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, passage.collapsingWhitespace())
    return Intent.createChooser(send, null)
}
