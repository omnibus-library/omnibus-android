package com.omnibus.omnibus.feature.bookdetail.overview.chips.edit

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.metadata.MetadataListKind
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Rule
import org.junit.Test

/** The dashed "+" chip: its names and role. */
class AddChipTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-62
    @Test
    fun issue101_ac7_addChipsHaveTheirDescriptionsAndButtonRole() {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                AddChip(MetadataListKind.Genres, enabled = true, onClick = {})
                AddChip(MetadataListKind.Tags, enabled = true, onClick = {})
            }
        }

        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_add_genres))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        rule.onNodeWithContentDescription(detailText(R.string.book_detail_chipedit_add_tags))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }
}
