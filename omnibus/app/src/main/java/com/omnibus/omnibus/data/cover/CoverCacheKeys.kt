package com.omnibus.omnibus.data.cover

import java.security.MessageDigest

/** Cache keys for cover thumbnails. */
object CoverCacheKeys {

    /** How long a cached cover is trusted before the server is asked whether it changed. */
    const val REVALIDATE_AFTER_MILLIS = 5 * 60 * 1000L

    /**
     * The disk key for an API [path]: its SHA-256 as lowercase hex. A digest rather than
     * `String.hashCode()`, so the key is the same in every process and the disk tier still
     * resolves after a restart.
     */
    fun diskKey(path: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(path.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** The disk key of the [uuid] book's thumbnail at [size]. */
    fun diskKey(uuid: String, size: CoverSize): String = diskKey(size.path(uuid))

    /**
     * The in-memory key for [thumbnail] at [nowMillis]. It embeds the current revalidation window,
     * so an entry decoded in an earlier window misses and the request goes back through the
     * repository, which revalidates it. A memory hit would otherwise never be re-checked.
     */
    fun memoryKey(thumbnail: CoverThumbnail, nowMillis: Long): String =
        "${diskKey(thumbnail.uuid, thumbnail.size)}$MEMORY_KEY_SEPARATOR${nowMillis / REVALIDATE_AFTER_MILLIS}"

    /** The disk key a [memoryKey] was derived from. */
    fun diskKeyOfMemoryKey(memoryKey: String): String = memoryKey.substringBefore(MEMORY_KEY_SEPARATOR)

    private const val MEMORY_KEY_SEPARATOR = "@"
}
