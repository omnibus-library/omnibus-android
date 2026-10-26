package com.omnibus.omnibus.data.cover

/**
 * A thumbnail size served by `GET /api/thumbs/{uuid}/{size}`. All sizes are WebP at 2:3.
 *
 * @property apiName The `{size}` path segment.
 * @property widthPx Width of the served image, in pixels.
 * @property heightPx Height of the served image, in pixels.
 */
enum class CoverSize(val apiName: String, val widthPx: Int, val heightPx: Int) {
    Sm("sm", 160, 240),
    Md("md", 320, 480),
    Lg("lg", 640, 960);

    /**
     * The sizes to try, in order, when this one is missing: every other size, largest first.
     * Upscaling a bigger copy of a cover reads better than blowing up a smaller one.
     */
    val fallbacks: List<CoverSize>
        get() = entries.sortedByDescending { it.widthPx }.filter { it != this }

    /** The relative API path of the [uuid] book's thumbnail at this size. */
    fun path(uuid: String): String = "api/thumbs/$uuid/$apiName"
}
