package com.omnibus.omnibus.feature.requests.start

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.feature.bookdetail.SectionContextRecorder
import com.omnibus.omnibus.feature.bookdetail.testDetailBook
import com.omnibus.omnibus.feature.bookdetail.testSectionContext
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class RequestStartContentTest {

    @get:Rule
    val rule = createComposeRule()

    private val recorder = SectionContextRecorder()
    private val opened = mutableListOf<Unit>()
    private val shown = mutableListOf<RequestStartMessage>()

    /** The stage host on the Overview tab, for a book that has a copy, so it never shows buttons. */
    private fun showStage(state: RequestStartUIState) {
        val context = testSectionContext(book = testDetailBook(uniqueIdentifier = "book-1"), recorder = recorder)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                RequestStartContent(
                    RequestStartSection.Host.Stage, context, state, offersSlots = false,
                    onStart = {}, onOpened = { opened += Unit }, onMessageShown = { shown += it },
                    modifier = Modifier,
                )
            }
        }
        rule.waitForIdle()
    }

    @Test
    fun stageHost_opensTheRequestPageForAStartThatAnswersOnOverviewOfABookWithACopy() {
        showStage(RequestStartUIState(pendingOpen = "book-1"))

        assertEquals(listOf(BookDetailDestination.Request("book-1")) to 1, recorder.navigations to opened.size)
    }

    @Test
    fun stageHost_showsAFailedStartsMessageOnOverviewOfABookWithACopy() {
        val message = RequestStartMessage("Couldn’t start the request", id = 1)

        showStage(RequestStartUIState(message = message))

        assertEquals(listOf("Couldn’t start the request") to listOf(message), recorder.messages.map { it.text } to shown)
    }
}
