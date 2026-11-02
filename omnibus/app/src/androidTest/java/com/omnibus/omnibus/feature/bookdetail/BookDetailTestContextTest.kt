package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.data.library.Book
import org.junit.Assert.assertEquals
import org.junit.Test

class BookDetailTestContextTest {

    // TC-124
    @Test
    fun issue93_tc124_testSectionContextRecordsCallbacks() {
        val defaults = testSectionContext()
        assertEquals(testDetailBook(), defaults.book)
        assertEquals(0, defaults.reloadKey)

        val recorder = SectionContextRecorder()
        val context = testSectionContext(recorder = recorder)
        val changed: Book = testDetailBook(id = 2, title = "Changed")
        context.onNavigate(BookDetailDestination.Reader("u"))
        context.onNavigate(BookDetailDestination.Author(7L, "A"))
        context.onShowMessage(BookDetailMessage("m"))
        context.onBookChanged(changed)
        context.onReloadBook()
        context.onReloadBook()
        context.onClose()

        assertEquals(
            listOf<BookDetailDestination>(BookDetailDestination.Reader("u"), BookDetailDestination.Author(7L, "A")),
            recorder.navigations,
        )
        assertEquals(listOf(BookDetailMessage("m")), recorder.messages)
        assertEquals(listOf(changed), recorder.changedBooks)
        assertEquals(2, recorder.reloadCount)
        assertEquals(1, recorder.closeCount)

        val other = testDetailBook(id = 3)
        val overridden = testSectionContext(book = other, reloadKey = 3)
        assertEquals(other, overridden.book)
        assertEquals(3, overridden.reloadKey)
    }
}
