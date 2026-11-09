package com.omnibus.omnibus.feature.reader.ui.curl

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

/** Captures [FakeImage]s named `snap-1`, `snap-2`, ..., logging each capture to [log]. Always drawn. */
internal class FakeSnapshotter(private val log: MutableList<String>) : PageSnapshotter {
    private var captures = 0

    override suspend fun capture(): PageImage? {
        log += "snapshot"
        captures++
        return FakeImage("snap-$captures")
    }

    override suspend fun awaitDrawn(): Boolean = true
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
