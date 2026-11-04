package com.omnibus.omnibus.data.audiobook.playback.media3

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Writes silent PCM WAV files, so the instrumented tests need no audio assets. */
internal object SilentWav {

    private const val SAMPLE_RATE = 8_000
    private const val BYTES_PER_SAMPLE = 2
    private const val HEADER_BYTES = 44

    /** Writes a mono 8 kHz 16-bit WAV of [seconds] of silence to [file]. */
    fun write(file: File, seconds: Double) {
        val dataBytes = (seconds * SAMPLE_RATE).toInt() * BYTES_PER_SAMPLE
        val header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(HEADER_BYTES - 8 + dataBytes)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1) // PCM
            putShort(1) // mono
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * BYTES_PER_SAMPLE)
            putShort(BYTES_PER_SAMPLE.toShort())
            putShort(16)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
        FileOutputStream(file).use { out ->
            out.write(header)
            out.write(ByteArray(dataBytes))
        }
    }
}
