package com.omnibus.omnibus.feature.scan

import androidx.annotation.StringRes
import com.omnibus.omnibus.R

/** The words of a close-match screen: its heading and subtitle, how the ISBN was found, and its two actions. */
internal data class CloseMatchCopy(
    @param:StringRes val heading: Int,
    @param:StringRes val subtitle: Int,
    @param:StringRes val subject: Int,
    @param:StringRes val found: Int,
    @param:StringRes val pick: Int,
    @param:StringRes val decline: Int,
)

/**
 * The close-match words for a scan found [via], for one candidate or, when [several], for a list. The subject follows
 * how the ISBN was found, as web `closeMatchCopy` does.
 */
internal fun closeMatchCopy(via: FoundVia, several: Boolean): CloseMatchCopy = CloseMatchCopy(
    heading = if (several) R.string.scan_close_match_several else R.string.scan_close_match_one,
    subtitle = if (several) R.string.scan_close_match_subtitle_several else R.string.scan_close_match_subtitle_one,
    subject = when (via) {
        FoundVia.Scan -> R.string.scan_close_match_subject_scan
        FoundVia.Manual -> R.string.scan_close_match_subject_manual
        FoundVia.Search -> R.string.scan_close_match_subject_search
    },
    found = when (via) {
        FoundVia.Scan -> R.string.scan_found_scan
        FoundVia.Manual -> R.string.scan_found_manual
        FoundVia.Search -> R.string.scan_found_search
    },
    pick = if (several) R.string.scan_this_one else R.string.scan_yes_thats_it,
    decline = if (several) R.string.scan_none_of_these else R.string.scan_no_different_book,
)
