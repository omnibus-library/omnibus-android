package com.omnibus.omnibus.feature.quotecard

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.ui.theme.Oklch
import java.io.File
import kotlin.math.abs
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class QuoteCardExportTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority get() = context.packageName + ".bookexport"
    private val folder get() = File(context.cacheDir, "book-exports/quote-card-test")

    @After
    fun deleteFiles() {
        folder.deleteRecursively()
    }

    private fun savedCard(): File {
        folder.mkdirs()
        return File(folder, "omnibus-quote.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
    }

    /** Composes the offscreen card for [shape] in Ember, and returns its exporter. */
    private fun exporterFor(shape: QuoteShape): QuoteCardExporter {
        lateinit var exporter: QuoteCardExporter
        rule.setContent {
            exporter = rememberQuoteCardExporter()
            // Something visible beside the offscreen card, as the sheet has: a window with no size is never drawn.
            Box(Modifier.size(10.dp))
            exporter.Offscreen(shape) { modifier ->
                QuoteCard(
                    passage = "It was the best of times, it was the worst of times.",
                    title = "A Tale of Two Cities",
                    author = "Charles Dickens",
                    fill = QuoteBackground.Preset(QuoteGradientPreset.Ember).fill(Oklch(0.5, 0.1, 30.0)),
                    typeface = QuoteTypeface.Serif,
                    size = QuoteSize.M,
                    bold = false,
                    italic = false,
                    shape = shape,
                    modifier = modifier,
                )
            }
        }
        rule.waitForIdle()
        return exporter
    }

    @Test
    fun capture_rendersThePostCardAs1080By1350OpaquePngFilledWithItsGradient() {
        val png = runBlocking { exporterFor(QuoteShape.Post).capture() }

        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertNotNull("the bytes are not an image", bitmap)
        assertEquals(1080, bitmap.width)
        assertEquals(1350, bitmap.height)
        val corner = bitmap.getPixel(0, 0)
        assertEquals("the centre is transparent", 0xFF, bitmap.getPixel(540, 675) ushr 24)
        // The top-left corner is the gradient's first stop, #7A1F16.
        listOf(16 to 0x7A, 8 to 0x1F, 0 to 0x16).forEach { (shift, expected) ->
            val channel = (corner ushr shift) and 0xFF
            assertTrue("channel at $shift was $channel, expected about $expected", abs(channel - expected) <= 3)
        }
    }

    @Test
    fun capture_drawsThePassageInLightInkOverTheGradient() {
        val png = runBlocking { exporterFor(QuoteShape.Post).capture() }

        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        // Light ink is #F5F0E8; the Ember gradient never gets near it, so any such pixel is a glyph.
        val inkPixels = (400 until 900).sumOf { y ->
            (0 until 1080).count { x ->
                val pixel = bitmap.getPixel(x, y)
                (pixel shr 16 and 0xFF) > 0xD0 && (pixel shr 8 and 0xFF) > 0xD0 && (pixel and 0xFF) > 0xC0
            }
        }
        assertTrue("no ink in the passage band: $inkPixels pixels", inkPixels > 2_000)
    }

    @Test
    fun capture_isStoryHeightForTheStoryShape() {
        val png = runBlocking { exporterFor(QuoteShape.Story).capture() }

        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertEquals(1080, bitmap.width)
        assertEquals(1920, bitmap.height)
    }

    @Test
    fun quoteCardShareIntent_isAnImagePngChooserOverAContentUriWithAReadGrant() {
        val chooser = quoteCardShareIntent(context, savedCard())

        @Suppress("DEPRECATION")
        val inner = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("image/png", inner.type)
        @Suppress("DEPRECATION")
        val stream = inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM).toString()
        assertTrue("uri was $stream", stream.startsWith("content://$authority/"))
        assertTrue("grant flag missing", (inner.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
    }

    @Test
    fun quoteCardClip_holdsTheSameContentUriTheShareSends() {
        val file = savedCard()

        val clip = quoteCardClip(context, file)

        @Suppress("DEPRECATION")
        val shared = quoteCardShareIntent(context, file)
            .getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        assertEquals(1, clip.itemCount)
        assertEquals(shared, clip.getItemAt(0).uri)
    }

    @Test
    fun passageShareIntent_sendsTheCollapsedPassageAsPlainTextAndNothingElse() {
        val chooser = passageShareIntent("  It was\n\n the   best \t of times.  ")

        @Suppress("DEPRECATION")
        val inner = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        assertEquals("It was the best of times.", inner.getStringExtra(Intent.EXTRA_TEXT))
        @Suppress("DEPRECATION")
        assertEquals(null, inner.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
    }
}
