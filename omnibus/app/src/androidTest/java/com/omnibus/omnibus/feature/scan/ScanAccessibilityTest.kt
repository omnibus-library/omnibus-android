package com.omnibus.omnibus.feature.scan

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.feature.scan.camera.CameraAvailability
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ScanAccessibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private fun setStage(
        stage: ScanStage,
        camera: FakeBarcodeCamera = FakeBarcodeCamera(),
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ScanScreenContent(
                    uiState = ScanUIState(stage = stage),
                    camera = camera,
                    actions = ScanActions(),
                    wide = false,
                    reducedMotion = true,
                )
            }
        }
    }

    /** Every node a reader can click or toggle on the stage is at least 48 dp high and wide. */
    private fun assertEveryTargetIsAtLeast48dp() {
        rule.waitForIdle()
        val minimumPx = with(rule.density) { 48.dp.toPx() }
        val targets = rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()
        assertTrue("the stage has no clickable nodes to check", targets.isNotEmpty())
        targets.forEach { node ->
            val bounds = node.boundsInRoot
            assertTrue(
                "a target is ${bounds.width} x ${bounds.height} px, below $minimumPx px",
                bounds.height >= minimumPx && bounds.width >= minimumPx,
            )
        }
    }

    // TC-42
    @Test
    fun issue317_tc42_viewfinderTargetsAreAtLeast48dp() {
        setStage(
            ScanStage.Viewfinder(lookupFailed = LookupFailure("9780393636529", FoundVia.Scan)),
            camera = FakeBarcodeCamera(hasFlash = true),
        )

        assertEveryTargetIsAtLeast48dp()
    }

    // TC-42
    @Test
    fun issue317_tc42_manualTargetsAreAtLeast48dp() {
        setStage(ScanStage.Manual(text = "978-0-306-40615-7"))

        assertEveryTargetIsAtLeast48dp()
    }

    // TC-42
    @Test
    fun issue317_tc42_unavailableTargetsAreAtLeast48dp() {
        setStage(ScanStage.Viewfinder(), camera = FakeBarcodeCamera(availability = CameraAvailability.PermanentlyDenied))

        assertEveryTargetIsAtLeast48dp()
    }

    // TC-42
    @Test
    fun issue317_tc42_inLibraryOutcomeTargetsAreAtLeast48dp() {
        setStage(ScanStage.Outcome(ScanOutcome.InLibraryUnowned(scanBook()), "9780393636529", FoundVia.Scan))

        assertEveryTargetIsAtLeast48dp()
    }

    // TC-42
    @Test
    fun issue317_tc42_onWishlistOutcomeTargetsAreAtLeast48dp() {
        setStage(ScanStage.Outcome(ScanOutcome.OnWishlist(scanBook()), "9780393636529", FoundVia.Scan))

        assertEveryTargetIsAtLeast48dp()
    }

    // TC-42
    @Test
    fun issue317_tc42_successTargetsAreAtLeast48dp() {
        val book = scanBook()
        setStage(ScanStage.Success(book.title, SuccessCover.Library(book), book.uuid, SuccessKind.CheckedIn))

        assertEveryTargetIsAtLeast48dp()
    }

    // TC-43
    @Test
    fun issue317_tc43_viewfinderTitleAnnouncesAndDescribesTheCamera() {
        setStage(ScanStage.Viewfinder())

        rule.onNodeWithTag(ScanTestTags.TITLE, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        rule.onNodeWithTag(ScanTestTags.VIEWFINDER, useUnmergedTree = true).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ContentDescription,
                listOf(scanText(R.string.scan_viewfinder_description)),
            ),
        )
    }

    // TC-43
    @Test
    fun issue317_tc43_outcomeTitleIsAPoliteHeading() {
        setStage(ScanStage.Outcome(ScanOutcome.AlreadyOwned(scanBook()), "9780393636529", FoundVia.Scan))

        rule.onNodeWithTag(ScanTestTags.TITLE, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    // TC-43
    @Test
    fun issue317_tc43_successTitleIsAPoliteHeadingAndTheCelebrationIsSilent() {
        val book = scanBook()
        setStage(ScanStage.Success(book.title, SuccessCover.Library(book), book.uuid, SuccessKind.CheckedIn))

        rule.onNodeWithTag(ScanTestTags.TITLE, useUnmergedTree = true)
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        // The merged tree is what TalkBack reads: the canvas sits under a node that clears semantics, so it isn't in it.
        rule.onNodeWithTag(ScanTestTags.CELEBRATION).assertDoesNotExist()
    }
}
