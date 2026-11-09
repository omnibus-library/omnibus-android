package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.ui.components.OmnibusTabBar
import com.omnibus.omnibus.ui.components.OmnibusTabBarItem

/** The tag on the box [ShellTestOverlay] draws, so a test can measure the overlay. */
internal const val SHELL_OVERLAY_TAG = "shell-test-overlay"

/**
 * The real tab bar as the shell's bottom overlay, in a tagged box. [grown] adds a row above the bar, as a mini
 * player does when it shows.
 */
@Composable
internal fun ShellTestOverlay(grown: Boolean = false) {
    val items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
    Box(Modifier.testTag(SHELL_OVERLAY_TAG)) {
        Column {
            if (grown) Text("Mini player")
            OmnibusTabBar(items = items, selectedIndex = 0, onSelect = {})
        }
    }
}
