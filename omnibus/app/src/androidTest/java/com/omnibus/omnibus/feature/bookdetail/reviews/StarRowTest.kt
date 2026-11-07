package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The star row: its one description for the rating. */
class StarRowTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-45 (AC7)
    @Test
    fun tc45_describesTheRoundedRatingAsOneNode() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxWidth()) {
                    StarRow(3.3f, 18.dp, Color.Red, Modifier.testTag("row"))
                }
            }
        }

        val row = rule.onNodeWithTag("row", useUnmergedTree = true)
        row.assertContentDescriptionEquals(detailText(R.string.book_detail_reviews_stars_description, "3.5"))
        assertTrue(row.fetchSemanticsNode().children.none { it.config.contains(SemanticsProperties.ContentDescription) })
    }
}
