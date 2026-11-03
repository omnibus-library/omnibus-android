package com.omnibus.omnibus.feature.bookdetail.overview.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.components.ReaderAvatar
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale
import java.util.TimeZone

/**
 * The household's activity on this book, on the Overview tab: each other reader who shares their stats and has spent
 * time in it. Renders nothing for a book with no copy to read or listen to, before it creates its ViewModel, so such
 * a book needs no Hilt, and nothing while there are no readers to list.
 */
internal object HouseholdActivitySection : BookDetailSection {

    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        if (!showsHouseholdActivity(context.book)) return
        val viewModel = hiltViewModel<HouseholdActivityViewModel, HouseholdActivityViewModel.Factory>(
            key = "household-activity-${context.book.uuid}",
            creationCallback = { it.create(context.book.uuid) },
        )
        LaunchedEffect(context.reloadKey) { viewModel.reload(context.reloadKey) }
        val readers by viewModel.readers.collectAsState()
        HouseholdActivity(readers, modifier)
    }
}

/** Whether others could have read the book: it has an ebook, an audiobook or a physical copy. */
internal fun showsHouseholdActivity(book: Book): Boolean = book.hasEbook || book.hasAudiobook || book.hasPhysical

/** The Activity heading and one row per reader, or nothing for an empty list. */
@Composable
internal fun HouseholdActivity(readers: List<HouseholdReader>, modifier: Modifier = Modifier) {
    if (readers.isEmpty()) return
    val palette = OmnibusTheme.palette
    val resources = LocalContext.current.resources
    val configuration = LocalConfiguration.current
    val wording = remember(resources, configuration) {
        ActivityWording(
            lastRead = { day -> resources.getString(R.string.book_detail_activity_last_read, day) },
            sessions = { count ->
                resources.getQuantityString(R.plurals.book_detail_activity_sessions, count.toPluralInt(), count.toPluralInt())
            },
            underAMinute = resources.getString(R.string.book_detail_activity_under_a_minute),
        )
    }
    val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
    val timeZone = remember { TimeZone.getDefault() }
    val nowMillis = remember(readers) { System.currentTimeMillis() }

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 28.dp)
            .testTag(HouseholdActivityTestTags.SECTION),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.book_detail_activity_title),
            style = displayTextStyle(22.sp, palette.ink0, FontWeight.SemiBold),
            modifier = Modifier
                .semantics { heading() }
                .testTag(HouseholdActivityTestTags.TITLE),
        )
        readers.forEach { reader ->
            // The row reads as one item: name, then the line. The avatar is decorative.
            Row(
                Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {}
                    .testTag(HouseholdActivityTestTags.row(reader.userId)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                ReaderAvatar(reader.userId, reader.name, reader.hasAvatar, size = 32.dp)
                Column {
                    Text(reader.name, style = uiTextStyle(15.sp, FontWeight.SemiBold, palette.ink0))
                    Text(
                        text = activityLine(reader, nowMillis, locale, timeZone, wording),
                        style = uiTextStyle(14.sp, FontWeight.Normal, palette.ink1),
                    )
                }
            }
        }
    }
}

/** A session count as the Int that plural rules take, clamped for the rare count beyond Int. */
private fun Long.toPluralInt(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
