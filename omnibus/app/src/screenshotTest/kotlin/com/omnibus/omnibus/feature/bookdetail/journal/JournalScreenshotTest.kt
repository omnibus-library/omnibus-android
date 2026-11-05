package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

// Instants are at noon UTC, so they fall on the same date in every zone the shots could be taken in.
/** 2025-10-29 12:00 UTC, so the dates below read as this year. */
private const val NOW_MILLIS = 1_761_739_200_000L

/** 2025-10-24 12:00 UTC. */
private const val OCT_24_2025 = 1_761_307_200_000L

/** 2025-10-15 12:00 UTC. */
private const val OCT_15_2025 = 1_760_529_600_000L

/** 2025-10-09 12:00 UTC. */
private const val OCT_9_2025 = 1_760_011_200_000L

@PreviewTest
@PreviewLightDark
@Composable
fun JournalLoading() {
    JournalShot(JournalUIContent.Loading)
}

@PreviewTest
@PreviewLightDark
@Composable
fun JournalEmpty() {
    JournalShot(JournalUIContent.Loaded(emptyList()))
}

@PreviewTest
@PreviewLightDark
@Composable
fun JournalFailed() {
    JournalShot(JournalUIContent.Failed)
}

/** Someone else's entry, your own, your draft, one with no byline, and a veiled spoiler. */
@PreviewTest
@PreviewLightDark
@Composable
fun JournalCards() {
    JournalShot(
        JournalUIContent.Loaded(
            listOf(
                card(1, authorName = "Ann", isOwn = false, createdAtMillis = OCT_24_2025),
                card(2, authorName = "Ann", isOwn = true, createdAtMillis = OCT_15_2025),
                card(3, authorName = "Ann", isOwn = true, isDraft = true, createdAtMillis = OCT_9_2025),
                card(4, authorName = "", isOwn = true, createdAtMillis = null),
                card(5, authorName = "Ann", createdAtMillis = OCT_24_2025, preview = journalPreview("He was ||Ares||.")),
            ),
        ),
    )
}

private fun card(
    id: Long,
    authorName: String,
    isOwn: Boolean = false,
    isDraft: Boolean = false,
    createdAtMillis: Long?,
    preview: List<PreviewRun> = listOf(PreviewRun.Text("Passage $id")),
) = JournalCardModel(id, authorName, isOwn, isDraft, createdAtMillis, preview)

@Composable
private fun JournalShot(content: JournalUIContent) {
    Shot {
        JournalContent(JournalUIState(content, NOW_MILLIS), onRetry = {})
    }
}
