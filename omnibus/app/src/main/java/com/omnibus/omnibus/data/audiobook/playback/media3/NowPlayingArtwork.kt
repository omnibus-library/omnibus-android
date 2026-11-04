package com.omnibus.omnibus.data.audiobook.playback.media3

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

/**
 * The generated plate shown when a book has no cover (I-SYS-4): a square PNG in a colour chosen from the book's uuid, with
 * the title's initials centred. The last plate is cached, so each book is drawn once.
 */
internal object ArtworkPlate {

    /** The plate's width and height, in pixels. */
    const val SIZE_PX = 1024

    private val PALETTE = intArrayOf(
        0xFF1F3A5F.toInt(),
        0xFF3B2F4F.toInt(),
        0xFF2E4A3F.toInt(),
        0xFF5A3A2E.toInt(),
        0xFF2F3E46.toInt(),
        0xFF4A2C3F.toInt(),
    )

    private var cache: Pair<String, ByteArray>? = null

    /** The PNG bytes for [bookUuid], drawn from [title]. Returns the cached bytes when the uuid is the same. */
    fun render(bookUuid: String, title: String): ByteArray {
        cache?.takeIf { it.first == bookUuid }?.let { return it.second }
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(PALETTE[Math.floorMod(bookUuid.hashCode(), PALETTE.size)])
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
                textSize = SIZE_PX * 0.32f
            }
            val baseline = SIZE_PX / 2f - (paint.descent() + paint.ascent()) / 2f
            drawText(initials(title), SIZE_PX / 2f, baseline, paint)
        }
        val bytes = ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bitmap.recycle()
        cache = bookUuid to bytes
        return bytes
    }

    /** Up to two initials from [title], uppercase, or "?" when the title has no words. */
    internal fun initials(title: String): String = title
        .split(' ')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }
}
