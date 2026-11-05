package com.omnibus.omnibus.feature.bookdetail.overview.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.household.HouseholdReader
import com.omnibus.omnibus.screenshot.Shot

// No last session: the list dates it against the system clock, which would change the shot every day. The wording is
// covered by HouseholdActivityLineTest.
@PreviewTest
@PreviewLightDark
@Composable
fun HouseholdActivityReaders() {
    Shot {
        HouseholdActivity(
            listOf(
                HouseholdReader(3, "Maren", hasAvatar = false, seconds = 22_320, sessions = 4, lastSessionAtMillis = null),
                HouseholdReader(4, "theo", hasAvatar = false, seconds = 7_260, sessions = 1, lastSessionAtMillis = null),
            ),
            Modifier,
        )
    }
}
