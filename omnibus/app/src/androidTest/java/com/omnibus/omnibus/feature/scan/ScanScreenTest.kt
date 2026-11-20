package com.omnibus.omnibus.feature.scan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import org.junit.Assert.assertTrue
import kotlin.math.abs

class ScanScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setContent(
        stage: ScanStage = ScanStage.Viewfinder(),
        camera: FakeBarcodeCamera = FakeBarcodeCamera(),
        actions: ScanActions = ScanActions(),
        torchOn: Boolean = false,
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(stage = stage, torchOn = torchOn),
                    camera = camera,
                    actions = actions,
                    wide = false,
                    reducedMotion = true,
                )
            }
        }
    }

    // TC-5
    @Test
    fun issue317_tc5_requestsPermissionOnOpen() {
        val camera = FakeBarcodeCamera(availability = CameraAvailability.Unknown)
        setContent(camera = camera)

        rule.waitForIdle()

        assertEquals(1, camera.requestPermissionCalls)
        rule.onNodeWithTag(ScanTestTags.VIEWFINDER).assertDoesNotExist()
        rule.onNodeWithText(scanText(R.string.scan_camera_unavailable)).assertDoesNotExist()
    }

    // TC-6a
    @Test
    fun issue317_tc6a_unavailableOffersEnterIsbn() {
        val camera = FakeBarcodeCamera(availability = CameraAvailability.Denied)
        var enterIsbn = 0
        setContent(camera = camera, actions = ScanActions(onEnterIsbn = { enterIsbn++ }))
        rule.onNodeWithText(scanText(R.string.scan_camera_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_open_settings)).assertDoesNotExist()

        rule.runOnIdle { camera.availability = CameraAvailability.NoCamera }
        rule.waitForIdle()
        rule.onNodeWithText(scanText(R.string.scan_camera_unavailable)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_open_settings)).assertDoesNotExist()
        rule.onNodeWithText(scanText(R.string.scan_enter_isbn)).performClick()

        assertEquals(1, enterIsbn)
    }

    // TC-6b
    @Test
    fun issue317_tc6b_permanentlyDeniedOffersSettings() {
        val camera = FakeBarcodeCamera(availability = CameraAvailability.PermanentlyDenied)
        setContent(camera = camera)

        rule.onNodeWithText(scanText(R.string.scan_open_settings)).performClick()

        assertEquals(1, camera.openSettingsCalls)
    }

    // TC-14
    @Test
    fun issue317_tc14_rejectionNoticeIsAPoliteLiveRegion() {
        val camera = FakeBarcodeCamera()
        setContent(
            stage = ScanStage.Viewfinder(rejected = RejectedNotice("0123456789012", announce = true)),
            camera = camera,
        )

        rule.onNodeWithTag(ScanTestTags.NOTICE)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithText(scanText(R.string.scan_not_isbn)).assertIsDisplayed()
        rule.waitForIdle()
        assertEquals(false, camera.lastFrozen)
    }

    // TC-15a
    @Test
    fun issue317_tc15a_noTorchWithoutAFlash() {
        setContent(camera = FakeBarcodeCamera(hasFlash = false))

        rule.onNodeWithTag(ScanTestTags.TORCH).assertDoesNotExist()
    }

    // TC-15b
    @Test
    fun issue317_tc15b_torchIsASwitchWithItsState() {
        val toggles = mutableListOf<Unit>()
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                var torchOn by remember { mutableStateOf(false) }
                ScanScreenContent(
                    uiState = ScanUIState(torchOn = torchOn),
                    camera = FakeBarcodeCamera(hasFlash = true),
                    actions = ScanActions(onTorchToggled = {
                        toggles += Unit
                        torchOn = !torchOn
                    }),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }
        val torch = rule.onNodeWithTag(ScanTestTags.TORCH)

        torch.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        torch.assertIsOff()
        torch.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, scanText(R.string.scan_torch_off)))
        torch.performClick()
        rule.waitForIdle()

        assertEquals(1, toggles.size)
        torch.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, scanText(R.string.scan_torch_on)))
    }

    // TC-20
    @Test
    fun issue317_tc20_failedLookupBannerKeepsTheCamera() {
        val camera = FakeBarcodeCamera()
        var tries = 0
        setContent(
            stage = ScanStage.Viewfinder(lookupFailed = LookupFailure("9780393636529", FoundVia.Scan)),
            camera = camera,
            actions = ScanActions(onTryAgain = { tries++ }),
        )

        rule.onNodeWithText(scanText(R.string.scan_lookup_failed)).assertIsDisplayed()
        rule.onNodeWithText(scanText(R.string.scan_try_again)).performClick()

        assertEquals(1, tries)
        rule.onNodeWithTag("fake-camera").assertExists()
    }

    // TC-56
    @Test
    fun issue335_tc56_searchByTitleSitsBesideEnterIsbn() {
        var searched = 0
        setContent(actions = ScanActions(onSearchByTitle = { searched++ }))

        val enterIsbn = rule.onNode(hasText(scanText(R.string.scan_enter_isbn)) and hasClickAction()).getUnclippedBoundsInRoot()
        val searchByTitle = rule.onNode(hasText(scanText(R.string.scan_search_by_title)) and hasClickAction())
            .getUnclippedBoundsInRoot()

        assertTrue("Search by title is not on the row of Enter ISBN", abs((searchByTitle.top - enterIsbn.top).value) <= 1f)
        assertTrue("Search by title is not beside Enter ISBN", searchByTitle.left >= enterIsbn.right)
        rule.onNode(hasText(scanText(R.string.scan_search_by_title)) and hasClickAction()).performClick()
        assertEquals(1, searched)
    }
}
