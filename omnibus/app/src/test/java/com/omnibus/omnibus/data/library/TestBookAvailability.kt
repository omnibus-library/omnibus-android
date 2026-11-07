package com.omnibus.omnibus.data.library

import com.omnibus.omnibus.testing.randomBoolean

/** A [BookAvailability] that answers [answer] and records every question asked. */
class TestBookAvailability(var answer: Boolean = randomBoolean()) : BookAvailability {

    /** The `(bookUuid, medium)` of every [canOpen] call, in call order. */
    val calls = mutableListOf<Pair<String, ProgressFormat>>()

    override suspend fun canOpen(bookUuid: String, medium: ProgressFormat): Boolean {
        calls += bookUuid to medium
        return answer
    }
}
