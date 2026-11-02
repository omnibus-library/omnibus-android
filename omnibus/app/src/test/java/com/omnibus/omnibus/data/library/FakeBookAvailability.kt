package com.omnibus.omnibus.data.library

/** A [BookAvailability] that answers [answer] and records every question asked. */
class FakeBookAvailability(var answer: Boolean = true) : BookAvailability {

    /** The `(bookUuid, medium)` of every [canOpen] call, in call order. */
    val calls = mutableListOf<Pair<String, ProgressFormat>>()

    override suspend fun canOpen(bookUuid: String, medium: ProgressFormat): Boolean {
        calls += bookUuid to medium
        return answer
    }
}
