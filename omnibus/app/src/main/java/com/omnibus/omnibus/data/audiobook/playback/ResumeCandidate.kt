package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ResumePoint

/**
 * The book "play with nothing loaded" resumes (I-SYS-5, iOS `resumeCandidate`): the newest Listening point, only if it can
 * open now. An older point never stands in for it. Null when there is no Listening point or it can't open.
 */
internal suspend fun resumeCandidate(
    points: List<ResumePoint>,
    canOpen: suspend (bookUuid: String) -> Boolean,
): ResumePoint? {
    val latest = points.firstOrNull { it.record.format == ProgressFormat.Listening } ?: return null
    return latest.takeIf { canOpen(it.record.bookUuid) }
}
