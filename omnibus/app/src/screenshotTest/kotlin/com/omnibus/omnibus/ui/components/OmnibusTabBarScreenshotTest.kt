package com.omnibus.omnibus.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun OmnibusTabBarSearchSelected() {
    Shot {
        OmnibusTabBar(
            items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) },
            selectedIndex = ShellTab.entries.indexOf(ShellTab.Search),
            onSelect = {},
        )
    }
}
