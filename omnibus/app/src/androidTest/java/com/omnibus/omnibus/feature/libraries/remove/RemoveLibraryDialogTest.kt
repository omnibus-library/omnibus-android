package com.omnibus.omnibus.feature.libraries.remove

import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Waits until the dialog's window has focus, so Back reaches the dialog and not the activity. A dialog that has only
 * just opened may not have focus yet on a loaded emulator.
 */
private fun ComposeContentTestRule.waitForDialogFocus() {
    waitUntil(timeoutMillis = 5_000) {
        runCatching {
            Espresso.onView(ViewMatchers.isRoot())
                .inRoot(RootMatchers.isDialog())
                .check { view, _ ->
                    if (!view.hasWindowFocus()) throw AssertionError("The dialog window does not have focus yet")
                }
        }.isSuccess
    }
}

class RemoveLibraryDialogTest {

    @get:Rule
    val rule = createComposeRule()

    /** Shows the confirm dialog for "Home" with the given callbacks. */
    private fun showConfirm(
        isRemoving: Boolean = false,
        onCancel: () -> Unit = {},
        onRemove: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                RemoveLibraryConfirmDialog(
                    name = "Home",
                    onCancel = onCancel,
                    onRemove = onRemove,
                    isRemoving = isRemoving,
                )
            }
        }
    }

    /** Shows the sync-failed dialog for "Home" with the given callbacks. */
    private fun showSyncFailed(
        isRemoving: Boolean = false,
        onCancel: () -> Unit = {},
        onRemoveAnyway: () -> Unit = {},
    ) {
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                RemoveLibrarySyncFailedDialog(
                    name = "Home",
                    onCancel = onCancel,
                    onRemoveAnyway = onRemoveAnyway,
                    isRemoving = isRemoving,
                )
            }
        }
    }

    // TC-22 (R2, R3, R13)
    @Test
    fun issue373_tc22_titleReadsRemoveWithTheName() {
        showConfirm()

        rule.onNodeWithTag(RemoveLibraryTestTags.TITLE).assertTextEquals("Remove Home?")
    }

    @Test
    fun issue373_tc22_titleIsAHeading() {
        showConfirm()

        rule.onNodeWithTag(RemoveLibraryTestTags.TITLE).assert(isHeading())
    }

    @Test
    fun issue373_tc22_bodySaysWhatHappens() {
        showConfirm()

        rule.onNodeWithText("You'll be signed out of Home, and its data on this device will be deleted.")
            .assertIsDisplayed()
    }

    @Test
    fun issue373_tc22_cancelAndRemoveAreAtLeast48dpTall() {
        showConfirm()

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).assertHeightIsAtLeast(48.dp)
        rule.onNodeWithTag(RemoveLibraryTestTags.CONFIRM).assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun issue373_tc22_cancelFiresOnCancelOnce() {
        var cancels = 0
        showConfirm(onCancel = { cancels++ })

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).performClick()
        rule.waitForIdle()

        assertEquals(1, cancels)
    }

    @Test
    fun issue373_tc22_removeFiresOnRemoveOnce() {
        var removes = 0
        showConfirm(onRemove = { removes++ })

        rule.onNodeWithTag(RemoveLibraryTestTags.CONFIRM).performClick()
        rule.waitForIdle()

        assertEquals(1, removes)
    }

    @Test
    fun issue373_tc22_backCallsOnCancel() {
        var cancels = 0
        showConfirm(onCancel = { cancels++ })

        rule.waitForDialogFocus()
        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(1, cancels)
    }

    // TC-23 (R10, R13)
    @Test
    fun issue373_tc23_titleReadsCouldntSyncWithTheName() {
        showSyncFailed()

        rule.onNodeWithTag(RemoveLibraryTestTags.TITLE).assertTextEquals("Couldn't sync Home")
    }

    @Test
    fun issue373_tc23_titleIsAHeading() {
        showSyncFailed()

        rule.onNodeWithTag(RemoveLibraryTestTags.TITLE).assert(isHeading())
    }

    @Test
    fun issue373_tc23_titleIsAPoliteLiveRegion() {
        showSyncFailed()

        rule.onNodeWithTag(RemoveLibraryTestTags.TITLE)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun issue373_tc23_bodySaysWhatIsLost() {
        showSyncFailed()

        rule.onNodeWithText(
            "Some changes on this device haven't reached Home. If you remove it now, they'll be lost.",
        ).assertIsDisplayed()
    }

    @Test
    fun issue373_tc23_cancelFiresOnCancelOnce() {
        var cancels = 0
        showSyncFailed(onCancel = { cancels++ })

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).performClick()
        rule.waitForIdle()

        assertEquals(1, cancels)
    }

    @Test
    fun issue373_tc23_removeAnywayFiresOnRemoveAnywayOnce() {
        var removeAnyways = 0
        showSyncFailed(onRemoveAnyway = { removeAnyways++ })

        rule.onNodeWithTag(RemoveLibraryTestTags.CONFIRM).performClick()
        rule.waitForIdle()

        assertEquals(1, removeAnyways)
    }

    @Test
    fun issue373_tc23_backCallsOnCancel() {
        var cancels = 0
        showSyncFailed(onCancel = { cancels++ })

        rule.waitForDialogFocus()
        Espresso.pressBack()
        rule.waitForIdle()

        assertEquals(1, cancels)
    }

    // TC-24 (R4, R10, R13)
    @Test
    fun issue373_tc24_confirmShowsRemoving() {
        showConfirm(isRemoving = true)

        rule.onNodeWithText("Removing").assertIsDisplayed()
    }

    @Test
    fun issue373_tc24_confirmDisablesCancel() {
        showConfirm(isRemoving = true)

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).assertIsNotEnabled()
    }

    @Test
    fun issue373_tc24_confirmTapsFireNothing() {
        var cancels = 0
        var removes = 0
        showConfirm(isRemoving = true, onCancel = { cancels++ }, onRemove = { removes++ })

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).performClick()
        rule.onNodeWithTag(RemoveLibraryTestTags.CONFIRM).performClick()
        rule.waitForIdle()

        assertEquals(0, cancels)
        assertEquals(0, removes)
    }

    @Test
    fun issue373_tc24_confirmBackCallsNothing() {
        var cancels = 0
        var removes = 0
        showConfirm(isRemoving = true, onCancel = { cancels++ }, onRemove = { removes++ })

        rule.waitForDialogFocus()
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()

        assertEquals(0, cancels)
        assertEquals(0, removes)
    }

    @Test
    fun issue373_tc24_syncFailedShowsRemoving() {
        showSyncFailed(isRemoving = true)

        rule.onNodeWithText("Removing").assertIsDisplayed()
    }

    @Test
    fun issue373_tc24_syncFailedDisablesCancel() {
        showSyncFailed(isRemoving = true)

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).assertIsNotEnabled()
    }

    @Test
    fun issue373_tc24_syncFailedTapsFireNothing() {
        var cancels = 0
        var removeAnyways = 0
        showSyncFailed(isRemoving = true, onCancel = { cancels++ }, onRemoveAnyway = { removeAnyways++ })

        rule.onNodeWithTag(RemoveLibraryTestTags.CANCEL).performClick()
        rule.onNodeWithTag(RemoveLibraryTestTags.CONFIRM).performClick()
        rule.waitForIdle()

        assertEquals(0, cancels)
        assertEquals(0, removeAnyways)
    }

    @Test
    fun issue373_tc24_syncFailedBackCallsNothing() {
        var cancels = 0
        var removeAnyways = 0
        showSyncFailed(isRemoving = true, onCancel = { cancels++ }, onRemoveAnyway = { removeAnyways++ })

        rule.waitForDialogFocus()
        Espresso.pressBackUnconditionally()
        rule.waitForIdle()

        assertEquals(0, cancels)
        assertEquals(0, removeAnyways)
    }
}
