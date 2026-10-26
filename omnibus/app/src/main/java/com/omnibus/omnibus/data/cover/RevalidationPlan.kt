package com.omnibus.omnibus.data.cover

/** What to ask the server about a cached cover, given how old it is and what validator it has. */
sealed interface RevalidationPlan {

    /** Still fresh: use the cached bytes without a request. */
    data object Skip : RevalidationPlan

    /** Stale, with a validator: ask cheaply with `If-None-Match`; the server can answer 304. */
    data class Conditional(val etag: String) : RevalidationPlan

    /**
     * Stale, with no validator to offer: fetch the full body once. The response carries the
     * validator that puts the entry back on the conditional path.
     */
    data object Unconditional : RevalidationPlan

    companion object {

        /**
         * Plans the check-back for an entry stored [ageMillis] ago holding [etag] (or none).
         * A negative age, from a clock that moved backwards, counts as stale.
         */
        fun of(ageMillis: Long, etag: String?): RevalidationPlan = when {
            ageMillis in 0 until CoverCacheKeys.REVALIDATE_AFTER_MILLIS -> Skip
            etag.isNullOrEmpty() -> Unconditional
            else -> Conditional(etag)
        }
    }
}
