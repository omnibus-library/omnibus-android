package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.feature.bookdetail.BookDetailUIState
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.setDetailScreen
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import org.junit.Rule
import org.junit.Test

class BookDetailAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-95
    @Test
    fun issue93_tc95_backCoverAuthorAndSeriesAreDescribed() {
        rule.setDetailScreen(
            BookDetailUIState(
                book = testDetailBook(
                    title = "Dune Messiah",
                    creators = listOf(Creator("Frank Herbert", "author", null, 3L)),
                    series = "Dune",
                    seriesIndex = "3",
                ),
                isLoading = false,
            ),
        )

        rule.onNodeWithContentDescription(detailText(R.string.navigation_back)).assertIsDisplayed()
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_cover_description, "Dune Messiah"))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_author_description, "Frank Herbert"))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(
            detailText(
                R.string.book_detail_series_description,
                detailText(R.string.book_detail_series_line, "Dune", "3"),
            ),
        ).assertIsDisplayed()
    }
}
