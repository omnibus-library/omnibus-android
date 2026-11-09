package com.omnibus.omnibus.feature.reader.ui

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement

/** Calls into the reader page that answer: each gets an id, and its reply completes it. Main thread only. */
internal class ReaderPageCalls(private val timeoutMs: Long = TIMEOUT_MS) {
    private var nextId = 0
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonElement?>>()

    /** Runs [expr] through [evaluate] and waits for its value; null on timeout, a failed call, or no page. */
    suspend fun call(expr: String, evaluate: ((String) -> Unit)?): JsonElement? {
        val run = evaluate ?: return null
        val id = ++nextId
        val reply = CompletableDeferred<JsonElement?>()
        pending[id] = reply
        return try {
            run(ReaderScripts.hostCall(id, expr))
            withTimeoutOrNull(timeoutMs) { reply.await() }
        } finally {
            pending.remove(id)
        }
    }

    fun resolve(id: Int, value: JsonElement?) {
        pending.remove(id)?.complete(value)
    }

    /** Answers every pending call with null, for a page that went away. */
    fun failAll() {
        val calls = pending.values.toList()
        pending.clear()
        calls.forEach { it.complete(null) }
    }

    companion object {
        const val TIMEOUT_MS = 3_000L
    }
}
