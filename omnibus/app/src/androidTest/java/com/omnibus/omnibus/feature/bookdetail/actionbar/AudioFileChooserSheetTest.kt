package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.omnibus.omnibus.feature.bookdetail.more.pressBackKey
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Oklch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

private val accent = Oklch(0.62, 0.12, 40.0).toColor()

/** A continuing 412 MB m4b, a 468 MB m4b, and an mp3 whose size is unknown. */
private val choices = listOf(
    AudioFileChoice(1L, "The Salt Road (Narrated by Ana Reyes)", "m4b", 412_000_000L, continues = true),
    AudioFileChoice(2L, "The Salt Road (Full Cast Edition)", "m4b", 468_000_000L, continues = false),
    AudioFileChoice(3L, "the_salt_road_abridged.mp3", "mp3", 0L, continues = false),
)

/** Renders the sheet with [choices], recording its close and pick calls in order. */
private fun ComposeContentTestRule.setChooser(events: MutableList<String>) {
    setContent {
        OmnibusTheme(darkTheme = true) {
            AudioFileChooserSheet(
                choices = choices,
                accent = accent,
                onClose = { events += "close" },
                onPick = { events += "pick:${it.id}" },
            )
        }
    }
}

class AudioFileChooserSheetTest {

    @get:Rule
    val rule = createComposeRule()

    // TC-38
    @Test
    fun issue99_tc38_pickClosesThenReports() {
        val events = mutableListOf<String>()
        val open = mutableStateOf(true)
        rule.setContent {
            OmnibusTheme(darkTheme = true) {
                if (open.value) {
                    AudioFileChooserSheet(
                        choices = choices,
                        accent = accent,
                        onClose = {
                            events += "close"
                            open.value = false
                        },
                        onPick = { events += "pick:${it.id}" },
                    )
                }
            }
        }

        rule.onNodeWithTag(AudioFileChooserTestTags.row(2L)).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { events.size == 2 }
        rule.waitForIdle()

        assertEquals(listOf("close", "pick:2"), events)
    }

    // TC-40
    @Test
    fun issue99_tc40_dismissOpensNothing() {
        val events = mutableListOf<String>()
        rule.setChooser(events)

        rule.onNodeWithTag(AudioFileChooserTestTags.CLOSE).performClick()
        rule.waitUntil(timeoutMillis = 5_000) { events.isNotEmpty() }
        rule.waitForIdle()

        assertEquals(listOf("close"), events)
    }

    // TC-40
    @Test
    fun issue99_tc40_systemBackOpensNothing() {
        val events = mutableListOf<String>()
        rule.setChooser(events)

        rule.pressBackKey()
        rule.waitUntil(timeoutMillis = 5_000) { events.isNotEmpty() }
        rule.waitForIdle()

        assertEquals(listOf("close"), events)
    }

    // TC-41
    @Test
    fun issue99_tc41_secondTapDuringHideIgnored() {
        val events = mutableListOf<String>()
        rule.setChooser(events)

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(AudioFileChooserTestTags.row(1L)).performClick()
        rule.onNodeWithTag(AudioFileChooserTestTags.row(2L)).performClick()
        rule.mainClock.autoAdvance = true

        rule.waitUntil(timeoutMillis = 5_000) { events.size >= 2 }
        rule.waitForIdle()

        assertEquals(listOf("close", "pick:1"), events)
    }
}
