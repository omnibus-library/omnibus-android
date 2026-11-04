package com.omnibus.omnibus.data.audiobook.playback.media3

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The now-playing artwork: the generated plate and the session's cap on cover size (I-SYS-4). */
@OptIn(UnstableApi::class)
class NowPlayingArtworkTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun decode(bytes: ByteArray): Bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)!!

    @Test
    fun `draws a deterministic 1024 px plate (I-SYS-4)`() {
        // TC-105: the plate is a square 1024 px PNG, the same for the same book, a different colour for another book,
        // with the initials drawn in the middle.
        val first = ArtworkPlate.render("book-1", "The Book")
        val plate = decode(first)
        assertEquals(1024, plate.width)
        assertEquals(1024, plate.height)
        assertArrayEquals(first, ArtworkPlate.render("book-1", "The Book"))

        val corner = plate.getPixel(8, 8)
        val otherUuid = (2..60).map { "book-$it" }.first { uuid ->
            val other = decode(ArtworkPlate.render(uuid, "The Book"))
            val differs = other.getPixel(8, 8) != corner
            other.recycle()
            differs
        }
        assertNotEquals(corner, decode(ArtworkPlate.render(otherUuid, "The Book")).getPixel(8, 8))

        val hasWhiteInCentre = (256 until 768 step 8).any { y ->
            (256 until 768 step 8).any { x ->
                val pixel = plate.getPixel(x, y)
                Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200
            }
        }
        assertTrue("the initials were not drawn in the centre", hasWhiteInCentre)
        plate.recycle()
    }

    @Test
    fun `caps the cover at 1024 px (I-SYS-4)`() {
        // TC-105: a 3000 px cover loads no larger than the session's limit.
        val big = Bitmap.createBitmap(3_000, 3_000, Bitmap.Config.RGB_565).apply { eraseColor(Color.RED) }
        val png = ByteArrayOutputStream().use { out ->
            big.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        big.recycle()

        val loader = DataSourceBitmapLoader.Builder(context)
            .setMaximumOutputDimension(AudiobookPlaybackService.MAX_ARTWORK_PX)
            .build()
        val loaded = loader.decodeBitmap(png).get(WAIT_MILLIS, TimeUnit.MILLISECONDS)
        assertTrue("${loaded.width} x ${loaded.height} exceeds 1024 px", loaded.width <= 1024 && loaded.height <= 1024)
        loaded.recycle()
    }
}
