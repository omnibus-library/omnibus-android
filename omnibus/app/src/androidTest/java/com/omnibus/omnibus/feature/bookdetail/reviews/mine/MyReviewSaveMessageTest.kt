package com.omnibus.omnibus.feature.bookdetail.reviews.mine

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.BookDetailMessage
import com.omnibus.omnibus.feature.bookdetail.detailText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The snackbar a failed rating or delete posts: its wording, and that each failure posts once. */
class MyReviewSaveMessageTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-76 (R18, AC7)
    @Test
    fun tc76_eachFailurePostsItsWordingOnceAndNothingPostsForNone() {
        val failure = mutableStateOf<MyReviewSaveFailure?>(null)
        val recompose = mutableIntStateOf(0)
        val messages = mutableListOf<BookDetailMessage>()
        var shown = 0
        rule.setContent {
            // Reading the counter makes this composition run again without a change of failure.
            recompose.intValue
            MyReviewSaveMessage(failure.value, { messages += it }, { shown++ })
        }

        val rateOffline = MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline = true)
        val rateOther = MyReviewSaveFailure(MyReviewSaveFailure.Kind.Rate, offline = false)
        val deleteOffline = MyReviewSaveFailure(MyReviewSaveFailure.Kind.Delete, offline = true)
        val deleteOther = MyReviewSaveFailure(MyReviewSaveFailure.Kind.Delete, offline = false)

        rule.runOnIdle { failure.value = rateOffline }
        rule.waitForIdle()
        rule.runOnIdle { recompose.intValue++ }
        rule.waitForIdle()
        assertEquals(listOf(detailText(R.string.book_detail_rate_save_failed_offline)), messages.map { it.text })

        rule.runOnIdle { failure.value = null }
        rule.runOnIdle { failure.value = rateOther }
        rule.waitForIdle()
        rule.runOnIdle { failure.value = deleteOffline }
        rule.waitForIdle()
        rule.runOnIdle { failure.value = deleteOther }
        rule.waitForIdle()

        assertEquals(
            listOf(
                detailText(R.string.book_detail_rate_save_failed_offline),
                detailText(R.string.book_detail_rate_save_failed),
                detailText(R.string.book_detail_rate_delete_failed_offline),
                detailText(R.string.book_detail_rate_delete_failed),
            ),
            messages.map { it.text },
        )
        assertEquals(4, shown)
    }
}
