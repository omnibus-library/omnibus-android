package com.omnibus.omnibus.feature.scan

import com.omnibus.omnibus.R
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.enum

class CloseMatchCopyTest : DescribeSpec({

    describe("the words of a close match") {

        it("#335 TC-60: words the heading by the candidate count") {
            checkAll(Exhaustive.enum<FoundVia>(), Exhaustive.boolean()) { via, several ->
                closeMatchCopy(via, several).heading shouldBe
                    if (several) R.string.scan_close_match_several else R.string.scan_close_match_one
            }
        }

        it("#335 TC-60: words the subtitle by the candidate count") {
            checkAll(Exhaustive.enum<FoundVia>(), Exhaustive.boolean()) { via, several ->
                closeMatchCopy(via, several).subtitle shouldBe
                    if (several) R.string.scan_close_match_subtitle_several else R.string.scan_close_match_subtitle_one
            }
        }

        it("#335 TC-60: words the subject by how the ISBN was found") {
            checkAll(Exhaustive.enum<FoundVia>(), Exhaustive.boolean()) { via, several ->
                closeMatchCopy(via, several).subject shouldBe when (via) {
                    FoundVia.Scan -> R.string.scan_close_match_subject_scan
                    FoundVia.Manual -> R.string.scan_close_match_subject_manual
                    FoundVia.Search -> R.string.scan_close_match_subject_search
                }
            }
        }

        it("#335 TC-60: words the found line by how the ISBN was found") {
            checkAll(Exhaustive.enum<FoundVia>(), Exhaustive.boolean()) { via, several ->
                closeMatchCopy(via, several).found shouldBe when (via) {
                    FoundVia.Scan -> R.string.scan_found_scan
                    FoundVia.Manual -> R.string.scan_found_manual
                    FoundVia.Search -> R.string.scan_found_search
                }
            }
        }

        it("#335 TC-60: words the pick label by the candidate count") {
            checkAll(Exhaustive.enum<FoundVia>(), Exhaustive.boolean()) { via, several ->
                closeMatchCopy(via, several).pick shouldBe
                    if (several) R.string.scan_this_one else R.string.scan_yes_thats_it
            }
        }

        it("#335 TC-60: words the decline label by the candidate count") {
            checkAll(Exhaustive.enum<FoundVia>(), Exhaustive.boolean()) { via, several ->
                closeMatchCopy(via, several).decline shouldBe
                    if (several) R.string.scan_none_of_these else R.string.scan_no_different_book
            }
        }
    }
})
