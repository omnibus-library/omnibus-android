package com.omnibus.omnibus.feature.bookdetail.wishlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.wishlist.WishlistEntry
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.data.wishlist.WishlistState
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionBarModel
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionBarTestTags
import com.omnibus.omnibus.feature.bookdetail.actionbar.ActionLabel
import com.omnibus.omnibus.feature.bookdetail.actionbar.BookActionBar
import com.omnibus.omnibus.feature.bookdetail.actionbar.BookActionBarSection
import com.omnibus.omnibus.feature.bookdetail.actionbar.actionBarColors
import com.omnibus.omnibus.feature.bookdetail.detailText
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** A fileless book with the given physical flag and ISBN-13 (null for a blank search). */
private fun filelessBook(physical: Boolean = false, isbn13: String? = "9781234567897"): Book =
    testDetailBook(formats = emptyList()).copy(hasPhysical = physical, isbn13 = isbn13)

/** Renders [BookActionBarSection] for the book from [book], with [fake] as the wishlist repository. */
private fun ComposeContentTestRule.setSection(
    book: () -> Book,
    fake: FakeWishlistRepository,
    recorder: SectionContextRecorder = SectionContextRecorder(),
) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            CompositionLocalProvider(LocalWishlistRepository provides fake) {
                BookActionBarSection.Content(
                    testSectionContext(book = book(), recorder = recorder),
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

class WishlistActionBarTest {

    @get:Rule
    val rule = createComposeRule()

    private val findCopyLabel get() = detailText(R.string.book_detail_wishlist_find_a_copy)
    private val checkInLabel get() = detailText(R.string.book_detail_wishlist_check_in)

    private fun present() = WishlistState.Present(WishlistEntry(id = 1, addedAtMillis = null, source = WishlistSource.Scan))

    // TC-54
    @Test
    fun issue119_tc54_findCopyOpensTheStoreSearchByIsbn() {
        val book = filelessBook(isbn13 = "9781234567897")
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        val recorder = SectionContextRecorder()
        rule.setSection({ book }, fake, recorder)

        rule.onNodeWithTag(ActionBarTestTags.FIND_COPY).performClick()
        rule.waitForIdle()
        assertEquals(
            listOf(BookDetailDestination.ExternalUrl("https://www.amazon.com/s?k=9781234567897")),
            recorder.navigations,
        )
    }

    // TC-54
    @Test
    fun issue119_tc54_findCopyWithoutAnIsbnSearchesTitleAndAuthor() {
        val book = filelessBook(isbn13 = null).copy(title = "Tom & Jerry")
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        val recorder = SectionContextRecorder()
        rule.setSection({ book }, fake, recorder)

        rule.onNodeWithTag(ActionBarTestTags.FIND_COPY).performClick()
        rule.waitForIdle()
        assertEquals(
            listOf(BookDetailDestination.ExternalUrl("https://www.amazon.com/s?k=Tom+%26+Jerry+Ursula+K.+Le+Guin")),
            recorder.navigations,
        )
    }

    // TC-55
    @Test
    fun issue119_tc55_checkInOpensTheCheckInPageOnce() {
        val book = filelessBook()
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        val recorder = SectionContextRecorder()
        rule.setSection({ book }, fake, recorder)

        rule.onNodeWithTag(ActionBarTestTags.CHECK_IN).performClick()
        rule.waitForIdle()
        assertEquals(listOf(BookDetailDestination.CheckIn(book.uuid)), recorder.navigations)
    }

    // TC-56
    @Test
    fun issue119_tc56_bothPillsAreLabelledButtons() {
        val book = filelessBook()
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        rule.setSection({ book }, fake)

        val buttonRole = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
        rule.onNodeWithTag(ActionBarTestTags.FIND_COPY)
            .assertContentDescriptionEquals(findCopyLabel)
            .assertHasClickAction()
            .assertIsEnabled()
            .assert(buttonRole)
            .assert(isFocusable())
        rule.onNodeWithTag(ActionBarTestTags.CHECK_IN)
            .assertContentDescriptionEquals(checkInLabel)
            .assertHasClickAction()
            .assertIsEnabled()
            .assert(buttonRole)
            .assert(isFocusable())
    }

    // TC-57
    @Test
    fun issue119_tc57_unansweredOrFailedOrAbsentShowsTheNoCopyNote() {
        val book = mutableStateOf(filelessBook(physical = true))
        val fake = FakeWishlistRepository().apply { seed(book.value.uuid, WishlistState.Unknown) }
        rule.setSection({ book.value }, fake)

        val states = listOf(WishlistState.Unknown, WishlistState.Failed, WishlistState.Absent)
        for (state in states) {
            for (physical in listOf(true, false)) {
                rule.runOnIdle {
                    fake.seed(book.value.uuid, state)
                    book.value = filelessBook(physical = physical)
                }
                val expected = if (physical) {
                    detailText(R.string.book_detail_actionbar_physical_copy)
                } else {
                    detailText(R.string.book_detail_actionbar_no_copy)
                }
                rule.onNodeWithTag(ActionBarTestTags.NO_COPY).assertTextEquals(expected)
                rule.onAllNodesWithTag(ActionBarTestTags.FIND_COPY).assertCountEquals(0)
                rule.onAllNodesWithTag(ActionBarTestTags.CHECK_IN).assertCountEquals(0)
            }
        }
    }

    // TC-58
    @Test
    fun issue119_tc58_barSwitchesToTheWishlistVariantAndBackWithoutAReload() {
        val book = filelessBook()
        val fake = FakeWishlistRepository().apply { seed(book.uuid, WishlistState.Unknown) }
        rule.setSection({ book }, fake)
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY).assertIsDisplayed()

        rule.runOnIdle { fake.seed(book.uuid, present()) }
        rule.onNodeWithTag(ActionBarTestTags.FIND_COPY).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.NO_COPY).assertCountEquals(0)

        rule.runOnIdle { fake.seed(book.uuid, WishlistState.Absent) }
        rule.onNodeWithTag(ActionBarTestTags.NO_COPY).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.FIND_COPY).assertCountEquals(0)
    }

    // TC-59
    // The readable-copy path of BookActionBarSection needs Hilt (ActionBarViewModel), which a plain activity lacks. This
    // test checks the gate that keeps the wishlist out of it, and draws the readable bar's primary model.
    @Test
    fun issue119_tc59_aBookWithAFileShowsNoWishlistPillsAndNoAccess() {
        val book = testDetailBook(formats = listOf("epub"))
        val fake = FakeWishlistRepository().apply { seed(book.uuid, present()) }
        val variant = mutableStateOf<ActionBarModel.Wishlist?>(null)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                CompositionLocalProvider(LocalWishlistRepository provides fake) {
                    variant.value = rememberWishlistActionModel(book)
                    BookActionBar(
                        model = ActionBarModel.Primary(ActionLabel.StartReading, 0f, BookDetailDestination.Reader(book.uuid)),
                        colors = actionBarColors(book),
                        onNavigate = {},
                        modifier = Modifier.fillMaxWidth(),
                        reducedMotion = true,
                    )
                }
            }
        }

        rule.onNodeWithTag(ActionBarTestTags.BUTTON).assertIsDisplayed()
        rule.onAllNodesWithTag(ActionBarTestTags.FIND_COPY).assertCountEquals(0)
        rule.onAllNodesWithTag(ActionBarTestTags.CHECK_IN).assertCountEquals(0)
        assertEquals(null, variant.value)
        assertTrue("no repository access for a book with a file", fake.flowCalls.isEmpty())
    }
}
