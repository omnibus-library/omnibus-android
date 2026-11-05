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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Taps, semantics and scrolling. BookShelfCardScreenshotTest covers how a card looks. */
class BookShelfTest {

    @get:Rule
    val rule = createComposeRule()

    private fun identity(n: Int) = CoverIdentity(uuid = "uuid-$n", title = "Book $n", hasCover = false)

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
