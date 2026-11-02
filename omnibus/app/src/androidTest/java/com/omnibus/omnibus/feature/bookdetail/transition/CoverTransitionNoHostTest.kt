package com.omnibus.omnibus.feature.bookdetail.transition

import androidx.activity.ComponentActivity
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.feature.bookdetail.stage.BookStageCover
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailTestTags
import com.omnibus.omnibus.feature.library.ui.BookGridCell
import com.omnibus.omnibus.feature.library.ui.ContinueCarousel
import com.omnibus.omnibus.feature.library.ui.ContinueTestTags
import com.omnibus.omnibus.feature.library.ui.LibraryTestTags
import com.omnibus.omnibus.feature.library.ui.rememberContinueCarouselState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CoverTransitionNoHostTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val book: Book = testDetailBook(id = 1)

    @Composable
    private fun Covers(withProvider: Boolean) {
        val content: @Composable () -> Unit = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val cellWidth = maxWidth * 0.4f
                Column {
                    BookGridCell(book = book, width = cellWidth, onClick = {})
                    BookStageCover(book = book, lightGround = false)
                    ContinueCarousel(
                        cards = listOf(harnessCard(book)),
                        selectedId = harnessCard(book).id,
                        state = rememberContinueCarouselState { 1 },
                        onSettled = {},
                        onOpenBook = {},
                        onResume = {},
                        onShowContextMenu = {},
                    )
                }
            }
        }
        if (withProvider) {
            SharedTransitionLayout(Modifier.fillMaxSize()) {
                ProvideBookCoverTransition(this, reducedMotion = true, state = remember { BookCoverTransitionState() }) {
                    content()
                }
            }
        } else {
            content()
        }
    }

    // TC-19
    @Test
    fun issue122_tc19_composablesRenderWithoutAHost() {
        rule.setContent { OmnibusTheme(darkTheme = true) { Covers(withProvider = false) } }
        rule.waitForIdle()

        assertCoversRender()
    }

    // TC-20
    @Test
    fun issue122_tc20_reducedMotionProviderNeverReadsTheNavScope() {
        rule.setContent { OmnibusTheme(darkTheme = true) { Covers(withProvider = true) } }
        rule.waitForIdle()

        assertCoversRender()
    }

    private fun assertCoversRender() {
        rule.onAllNodesWithTag(LibraryTestTags.cover(book.id), useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(BookDetailTestTags.COVER, useUnmergedTree = true).assertCountEquals(1)
        rule.onAllNodesWithTag(ContinueTestTags.cover(harnessCard(book).id), useUnmergedTree = true).assertCountEquals(1)

        // The covers keep their 2:3 ratio.
        val grid = rule.onNodeWithTag(LibraryTestTags.cover(book.id), useUnmergedTree = true).rootRect()
        assertEquals(2f / 3f, grid.width / grid.height, 0.02f)
        val stage = rule.onNodeWithTag(BookDetailTestTags.COVER, useUnmergedTree = true).rootRect()
        assertEquals(2f / 3f, stage.width / stage.height, 0.02f)
    }
}
