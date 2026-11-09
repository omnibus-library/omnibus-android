package com.omnibus.omnibus.feature.reader.ui.curl

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A page image that counts how often it was recycled. */
internal class FakeImage(val name: String) : PageImage {
    var recycles = 0

    override fun recycle() {
        recycles++
    }
}

/**
 * Captures [FakeImage]s named `snap-1`, `snap-2`, ..., logging each capture to [log] and each wait for a draw as
 * "drawn". Every capture is kept in [images] so a test can see which were recycled.
 */
internal class FakeSnapshotter(private val log: MutableList<String>) : PageSnapshotter {
    private var captures = 0
    val images = mutableListOf<FakeImage>()

    /** What [awaitDrawn] answers; false stands for the visual-state wait timing out. */
    var drawn = true

    override suspend fun capture(): PageImage? {
        log += "snapshot"
        captures++
        return FakeImage("snap-$captures").also { images += it }
    }

    override suspend fun awaitDrawn(): Boolean {
        log += "drawn"
        return drawn
    }
}

/**
 * A stage that logs each cover as `cover:<image name or null>`, a curl's start as "curl" and its end as "curl:null".
 * [curlFrames] counts the frames drawn.
 */
internal class FakeStage(private val log: MutableList<String>) : CurlStage {
    override val widthPx: Int = 1080
    override val heightPx: Int = 2000
    override val density: Float = 3f

    var cover: PageImage? = null
        private set
    var curl: CurlPicture? = null
        private set
    var curlFrames = 0
        private set

    override fun showCover(image: PageImage?) {
        cover = image
        log += "cover:${(image as? FakeImage)?.name ?: "null"}"
    }

    override fun showCurl(picture: CurlPicture?) {
        if (picture != null) {
            if (curl == null) log += "curl"
            curlFrames++
        } else if (curl != null) {
            log += "curl:null"
        }
        curl = picture
    }
}

/** A frame clock that ticks every 16 ms of virtual time. */
internal class TestFrameClock : FrameClock {
    private var now = 0L

    override suspend fun awaitFrame(): Long {
        delay(16)
        now += 16_000_000L
        return now
    }
}

/** A page that answers the expressions in [replies] and logs every expression to [log]. Unscripted calls answer null. */
internal class FakePage(private val log: MutableList<String>) {
    val replies = mutableMapOf<String, suspend () -> JsonElement?>()

    suspend fun call(expr: String): JsonElement? {
        log += expr
        return replies[expr]?.invoke()
    }
}

/** The `whenSettled()` reply for a page with a [next] and a [prev] neighbour kind, as the glue sends it. */
internal fun settledJson(next: String = "page", prev: String = "section", columns: Int = 1): JsonElement =
    buildJsonObject {
        put("columns", columns)
        put("next", next)
        put("prev", prev)
    }
