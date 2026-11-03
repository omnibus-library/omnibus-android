package com.omnibus.omnibus.feature.bookdetail.journal

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.format.BookDetailFormat
import java.util.Locale
import java.util.TimeZone

/** 2025-10-24 00:00 UTC. */
internal const val OCT_24_2025 = 1_761_264_000_000L

/** 2025-10-15 00:00 UTC. */
internal const val OCT_15_2025 = 1_760_486_400_000L

/** 2025-10-09 00:00 UTC. */
internal const val OCT_9_2025 = 1_759_968_000_000L

/** 2025-10-29, so the dates above read as this year. */
internal const val NOW_MILLIS = 1_761_775_445_000L

/** The device's first locale, as the list reads it at run time. */
internal fun deviceLocale(): Locale =
    InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.locales[0]

/** The date of [millis] as a card writes it at [NOW_MILLIS] on this device. */
internal fun dateText(millis: Long): String =
    BookDetailFormat.longDate(millis, NOW_MILLIS, deviceLocale(), TimeZone.getDefault())

/** What TalkBack reads for a spoiler, from the app's string. */
internal fun spoilerHiddenText(): String = detailText(R.string.book_detail_journal_spoiler_hidden)

/** Matches every card node by its test tag prefix. */
internal val journalCardMatcher = SemanticsMatcher("journal card") {
    it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(JournalTestTags.CARD_PREFIX) == true
}

/** A card model for tests. The date defaults to [OCT_24_2025]. */
internal fun testCard(
    id: Long,
    authorName: String = "Ann",
    isOwn: Boolean = false,
    isDraft: Boolean = false,
    createdAtMillis: Long? = OCT_24_2025,
    preview: List<PreviewRun> = listOf(PreviewRun.Text("Passage $id")),
) = JournalCardModel(
    id = id,
    authorName = authorName,
    isOwn = isOwn,
    isDraft = isDraft,
    createdAtMillis = createdAtMillis,
    preview = preview,
)

/** The dp count of a pixel distance in this rule's density. */
internal fun ComposeContentTestRule.dpOf(px: Float): Float = px / density.density

/** The pixel count of [dp] in this rule's density. */
internal fun ComposeContentTestRule.pxOf(dp: Float): Float = dp * density.density
