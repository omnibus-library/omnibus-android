package com.omnibus.omnibus.feature.bookdetail.reviews

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.reviews.stars.StarRow
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The star row: its one description per rating, and which parts of each star are drawn in the tint. */
class StarRowTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-45 (AC7)
    @Test
    fun tc45_describesTheRoundedRatingAsOneNode() {
        val ratings = listOf(4.5f, 0f, 5f, 3.3f, 3.2f)
        val descriptions = listOf("4.5 out of 5 stars", "0 out of 5 stars", "5 out of 5 stars", "3.5 out of 5 stars", "3 out of 5 stars")
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Column(Modifier.fillMaxWidth()) {
                    ratings.forEachIndexed { index, rating ->
                        StarRow(rating, 18.dp, Color.Red, Modifier.testTag("r${index + 1}"))
                    }
                }
            }
        }

        ratings.indices.forEach { index ->
            val tag = "r${index + 1}"
            rule.onAllNodesWithTag(tag, useUnmergedTree = true).assertCountEquals(1)
            rule.onNodeWithTag(tag, useUnmergedTree = true).assertContentDescriptionEquals(descriptions[index])
            val node = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
            assertTrue(node.children.none { it.config.contains(SemanticsProperties.ContentDescription) })
        }
    }
}
