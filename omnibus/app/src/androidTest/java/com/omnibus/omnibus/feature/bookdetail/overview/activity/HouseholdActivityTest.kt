package com.omnibus.omnibus.feature.bookdetail.overview.activity

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Activity list: the one-item-per-reader semantics. HouseholdActivityScreenshotTest covers its look. */
class HouseholdActivityTest {

    @get:Rule
    val rule = createComposeRule()

    private val maren = HouseholdReader(3, "Maren", hasAvatar = false, seconds = 22_320, sessions = 4, lastSessionAtMillis = 1_761_000_000_000L)
    private val theo = HouseholdReader(4, "Theo", hasAvatar = false, seconds = 7_260, sessions = 1, lastSessionAtMillis = 1_761_775_445_000L)

    private fun show(readers: List<HouseholdReader>) = rule.setContent {
        OmnibusTheme(darkTheme = true) { HouseholdActivity(readers, Modifier) }
    }

    // TC-33 (AC4)
    @Test
    fun tc33_eachRowReadsAsOneItemNameThenStatus() {
        show(listOf(maren, theo))

        val row = rule.onNodeWithTag(HouseholdActivityTestTags.row(3)).fetchSemanticsNode()
        val texts = row.config[SemanticsProperties.Text].map { it.text }
        assertEquals(2, texts.size)
        assertEquals("Maren", texts[0])
    }
}
