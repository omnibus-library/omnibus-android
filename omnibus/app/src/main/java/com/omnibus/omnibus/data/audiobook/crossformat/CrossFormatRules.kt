package com.omnibus.omnibus.data.audiobook.crossformat

/** The pure rules for an ebook-to-audio offer (I-RES-7, P-SYNC-AC6). No coroutines, so each rule is tested directly. */
internal object CrossFormatRules {

    /**
     * Whether an ebook offer with [sourceClock] may show, given the clock last dismissed for the book (null when never).
     * An offer returns only when the source clock changes, in either direction (A2), not only when it moves forward as
     * iOS's rule does.
     */
    fun shouldOffer(sourceClock: Long, dismissed: Long?): Boolean = dismissed == null || sourceClock != dismissed

    /**
     * Whether a candidate for [candidateFileId] applies to the loaded file. A null candidate file never matches (iOS
     * `fileMatches`); the loaded file is already resolved (C1c), so this is one equality.
     */
    fun fileMatches(loadedFileId: Long, candidateFileId: Long?): Boolean =
        candidateFileId != null && candidateFileId == loadedFileId
}
