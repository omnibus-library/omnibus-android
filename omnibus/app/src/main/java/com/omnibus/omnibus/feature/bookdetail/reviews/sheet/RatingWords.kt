package com.omnibus.omnibus.feature.bookdetail.reviews.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.bookdetail.reviews.halfStarRound

/** The word the sheet shows under a rating. There is one for each half star from 0.5 to 5, in order. */
enum class RatingWord { NotForMe, DidntWork, HardGoing, HadItsMoments, MixedFeelings, LikedIt, LikedItALot, ReallyGood, LovedIt, AFavorite }

/** The word for [rating], after rounding to a half star. Null, zero and below have none. */
fun ratingWord(rating: Float?): RatingWord? {
    val r = halfStarRound(rating ?: return null)
    if (r <= 0f) return null
    return RatingWord.entries[(r * 2).toInt() - 1]
}

/** The word's copy in the app's language. */
@Composable
internal fun ratingWordText(word: RatingWord): String = stringResource(
    when (word) {
        RatingWord.NotForMe -> R.string.book_detail_review_sheet_word_not_for_me
        RatingWord.DidntWork -> R.string.book_detail_review_sheet_word_didnt_work
        RatingWord.HardGoing -> R.string.book_detail_review_sheet_word_hard_going
        RatingWord.HadItsMoments -> R.string.book_detail_review_sheet_word_had_its_moments
        RatingWord.MixedFeelings -> R.string.book_detail_review_sheet_word_mixed_feelings
        RatingWord.LikedIt -> R.string.book_detail_review_sheet_word_liked_it
        RatingWord.LikedItALot -> R.string.book_detail_review_sheet_word_liked_it_a_lot
        RatingWord.ReallyGood -> R.string.book_detail_review_sheet_word_really_good
        RatingWord.LovedIt -> R.string.book_detail_review_sheet_word_loved_it
        RatingWord.AFavorite -> R.string.book_detail_review_sheet_word_a_favorite
    },
)
