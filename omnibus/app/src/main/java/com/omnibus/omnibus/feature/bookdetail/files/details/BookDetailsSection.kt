package com.omnibus.omnibus.feature.bookdetail.files.details

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection
import com.omnibus.omnibus.feature.bookdetail.BookDetailSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.displayTextStyle
import java.util.Locale
import java.util.TimeZone

/**
 * The Details list at the end of the Files tab: the book's publisher, publication date, series, language, ISBN, when
 * it was added and its file, so the reader can check which edition they have. A row the record has nothing for is
 * left out, and the whole list with it when there are no rows. It reads only the book it is given, so it needs no
 * ViewModel and no Hilt.
 */
internal object BookDetailsSection : BookDetailSection {
    @Composable
    override fun Content(context: BookDetailSectionContext, modifier: Modifier) {
        val resources = LocalContext.current.resources
        val configuration = LocalConfiguration.current
        val locale = remember(configuration) { configuration.locales[0] ?: Locale.getDefault() }
        val book = context.book
        val items = remember(book, locale, resources) {
            bookDetailItems(
                book = book,
                locale = locale,
                timeZone = TimeZone.getDefault(),
                wording = DetailsWording(
                    seriesBook = { name, index -> resources.getString(R.string.book_detail_details_series_book, name, index) },
                    unknownLanguage = resources.getString(R.string.book_detail_details_language_unknown),
                ),
            )
        }
        BookDetails(items, modifier)
    }
}

/** The Details heading and one row per item, or nothing for an empty list. */
@Composable
internal fun BookDetails(items: List<DetailItem>, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    Column(modifier.fillMaxWidth().padding(top = 32.dp).testTag(BookDetailsTestTags.SECTION)) {
        Text(
            text = stringResource(R.string.book_detail_details_title),
            style = displayTextStyle(30.sp, OmnibusTheme.palette.ink0, FontWeight.SemiBold),
            modifier = Modifier
                .padding(bottom = 6.dp)
                .semantics { heading() }
                .testTag(BookDetailsTestTags.TITLE),
        )
        items.forEach { DetailRowView(it) }
    }
}
