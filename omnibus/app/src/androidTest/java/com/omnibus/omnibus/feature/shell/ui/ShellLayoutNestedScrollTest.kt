package com.omnibus.omnibus.feature.shell.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ShellLayoutNestedScrollTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    // #286 TC-10 (R4)
    @Test
    fun issue286_tc10_contentScrollReachesTheShell() {
        rule.runOnUiThread { rule.activity.enableEdgeToEdge() }
        val recorder = object : NestedScrollConnection {
            var total = 0f

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                total += consumed.y
                return Offset.Zero
            }
        }
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                ShellLayout(
                    overlay = { ShellTestOverlay() },
                    modifier = Modifier.fillMaxSize(),
                    contentNestedScroll = recorder,
                ) {
                    LazyColumn(Modifier.fillMaxSize().testTag(LIST_TAG)) {
                        items(100) { Text("Row $it", Modifier.fillMaxWidth().padding(16.dp)) }
                    }
                }
            }
        }
        rule.waitForIdle()

        rule.onNodeWithTag(LIST_TAG).performTouchInput {
            swipe(Offset(centerX, height * 0.5f), Offset(centerX, height * 0.1f), 300)
        }
        rule.waitForIdle()

        assertTrue("the shell saw a scroll of ${recorder.total}", recorder.total < 0f)
    }

    private companion object {
        const val LIST_TAG = "shell-nested-scroll-list"
    }
}
