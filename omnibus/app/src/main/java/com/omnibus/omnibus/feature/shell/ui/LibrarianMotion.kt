package com.omnibus.omnibus.feature.shell.ui

/** The loop position shown when nothing moves: the book is in place, with the librarian's eyes open. */
internal const val SCENE_REST_PHASE = 0.7f

/**
 * How far the librarian has shelved the book at [phase] of the loop, from 0 (still in hand) to 1 (in place). The book
 * waits, eases in, waits again, then eases back out for the next go.
 */
internal fun shelvedAmount(phase: Float): Float = when {
    phase < 0.15f -> 0f
    phase < 0.55f -> smoothstep((phase - 0.15f) / 0.4f)
    phase < 0.85f -> 1f
    else -> 1f - smoothstep((phase - 0.85f) / 0.15f)
}

private fun smoothstep(t: Float): Float = t * t * (3f - 2f * t)
