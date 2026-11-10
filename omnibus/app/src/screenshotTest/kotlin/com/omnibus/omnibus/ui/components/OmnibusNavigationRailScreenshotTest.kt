package com.omnibus.omnibus.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@Preview(name = "Light", widthDp = 160, heightDp = 400)
@Preview(name = "Dark", widthDp = 160, heightDp = 400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun OmnibusNavigationRailSearchSelected() {
    Shot {
        OmnibusNavigationRail(
            items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) },
            selectedIndex = ShellTab.entries.indexOf(ShellTab.Search),
            onSelect = {},
        )
    }
}
