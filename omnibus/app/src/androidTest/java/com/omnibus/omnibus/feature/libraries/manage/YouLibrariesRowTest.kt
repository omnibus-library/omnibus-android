package com.omnibus.omnibus.feature.libraries.manage

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.requests.RequestWording
import com.omnibus.omnibus.feature.you.YouScreenContent
import com.omnibus.omnibus.feature.you.YouScreenTestTags
import com.omnibus.omnibus.feature.you.YouUIState
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class YouLibrariesRowTest {

    @get:Rule
    val rule = createComposeRule()

    private fun showYou(showsRequests: Boolean, onOpenManageLibraries: () -> Unit = {}) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                YouScreenContent(
                    uiState = YouUIState(showsRequests = showsRequests, library = null),
                    wording = RequestWording(),
                    onOpenRequests = {},
                    onOpenManageLibraries = onOpenManageLibraries,
                )
            }
        }
    }

    // TC-35 (R1, R13)
    @Test
    fun issue368_tc35_showsTheRowBelowRequestsWhenRequestsShow() {
        showYou(showsRequests = true)

        val requests = rule.onNodeWithTag(YouScreenTestTags.REQUESTS_ROW).getUnclippedBoundsInRoot()
        val libraries = rule.onNodeWithTag(YouScreenTestTags.LIBRARIES_ROW).getUnclippedBoundsInRoot()
        assertTrue("The Libraries row should sit below the Requests row", requests.bottom <= libraries.top)
    }

    @Test
    fun issue368_tc35_showsTheRowWhenRequestsAreHidden() {
        showYou(showsRequests = false)

        rule.onNodeWithTag(YouScreenTestTags.LIBRARIES_ROW).assertTextEquals("Libraries")
    }

    @Test
    fun issue368_tc35_theRowIsOneButtonAtLeast48dpTall() {
        showYou(showsRequests = true)

        rule.onNodeWithTag(YouScreenTestTags.LIBRARIES_ROW)
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue368_tc35_tappingTheRowOpensManageLibraries() {
        var opens = 0
        showYou(showsRequests = true, onOpenManageLibraries = { opens++ })

        rule.onNodeWithTag(YouScreenTestTags.LIBRARIES_ROW).performClick()

        rule.runOnIdle { assertEquals(1, opens) }
    }
}
