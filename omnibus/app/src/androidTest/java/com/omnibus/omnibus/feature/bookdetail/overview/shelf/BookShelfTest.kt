package com.omnibus.omnibus.feature.bookdetail.overview.shelf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.bounds
import com.omnibus.omnibus.feature.bookdetail.textLayout
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BookShelfTest {

    @get:Rule
    val rule = createComposeRule()

    private fun identity(n: Int) = CoverIdentity(uuid = "uuid-$n", title = "Book $n", hasCover = false)

    private val longTitle = List(30) { "very" }.joinToString(" ") + " long title"

    private fun setCard(
        title: String = "The Dispossessed",
        subtitle: String? = "1974",
        onClick: () -> Unit = {},
        onClickLabel: String? = "Open book",
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                BookShelfCard(identity(1), title, subtitle, onClick, onClickLabel)
            }
        }
    }

    // #105 TC-28
    @Test
    fun tc28_cardShowsTheTitleAndSubtitleUnderTheCover() {
        setCard()

        val card = rule.onNodeWithTag(BookShelfTestTags.CARD).bounds()
        val title = rule.onNodeWithTag(BookShelfTestTags.TITLE, useUnmergedTree = true).bounds()
        val subtitle = rule.onNodeWithTag(BookShelfTestTags.SUBTITLE, useUnmergedTree = true).bounds()
        assertTrue("title below the cover", title.top >= card.top + with(rule.density) { 164.dp.toPx() } - 1)
        assertTrue("subtitle below the title", subtitle.top >= title.bottom - 1)
    }

    // #105 TC-29
    @Test
    fun tc29_cardIs112dpWideWithA164dpCover() {
        setCard()

        rule.onNodeWithTag(BookShelfTestTags.CARD).assertWidthIsEqualTo(112.dp).assertHeightIsAtLeast(164.dp)
    }

    // #105 TC-30
    @Test
    fun tc30_aLongTitleStopsAtTwoLines() {
        setCard(title = longTitle)

        assertEquals(2, rule.onNodeWithTag(BookShelfTestTags.TITLE, useUnmergedTree = true).textLayout().lineCount)
    }

    // #105 TC-31
    @Test
    fun tc31_withoutASubtitleNoSubtitleIsDrawn() {
        setCard(subtitle = null)

        rule.onAllNodesWithTag(BookShelfTestTags.SUBTITLE, useUnmergedTree = true).assertCountEquals(0)
    }

    // #105 TC-32
    @Test
    fun tc32_tappingTheCardClicksAndTheClickLabelIsExposed() {
        var clicks = 0
        setCard(onClick = { clicks++ }, onClickLabel = "Open book")

        rule.onNodeWithTag(BookShelfTestTags.CARD).performClick()

        assertEquals(1, clicks)
        val action = rule.onNodeWithTag(BookShelfTestTags.CARD).fetchSemanticsNode().config
            .getOrNull(SemanticsActions.OnClick)
        assertEquals("Open book", action?.label)
    }

    // #105 TC-33
    @Test
    fun tc33_theCardIsOneNodeAnnouncingTheTitleAndTheSubtitle() {
        setCard()

        val texts = rule.onNodeWithTag(BookShelfTestTags.CARD).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.Text).orEmpty().map { it.text }
        assertEquals(listOf("The Dispossessed", "1974"), texts)
    }

    private fun setRow(count: Int) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    BookShelfRow(
                        content = {
                            items((1..count).toList()) { n ->
                                BookShelfCard(identity(n), "Book $n", "19$n", {}, null)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    // #105 TC-34
    @Test
    fun tc34_theRowRunsOutThroughThePanelGutterAndTheFirstCardStaysInsideIt() {
        setRow(count = 3)

        val row = rule.onNodeWithTag(BookShelfTestTags.ROW).bounds()
        val first = rule.onAllNodesWithTag(BookShelfTestTags.CARD)[0].bounds()
        assertEquals("row starts at the window edge", 0f, row.left, 1f)
        assertEquals("first card lines up with the gutter", with(rule.density) { 16.dp.toPx() }, first.left, 1f)
        assertEquals("row ends at the window edge", rule.onRoot().bounds().right, row.right, 1f)
    }

    // #105 TC-35
    @Test
    fun tc35_aLongRowScrollsSidewaysToItsLastCard() {
        setRow(count = 20)
        rule.onNodeWithText("Book 1").assertExists()
        rule.onNodeWithText("Book 20").assertDoesNotExist()

        rule.onNodeWithTag(BookShelfTestTags.ROW).performScrollToIndex(19)

        rule.onNodeWithText("Book 20").assertExists()
    }
}
