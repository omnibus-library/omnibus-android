package com.omnibus.omnibus.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.feature.shell.ShellTab
import com.omnibus.omnibus.screenshot.Shot

/** The tab bar with each tab selected in turn, 360 wide: the selected colour, weight and pill (TC-55). */
@PreviewTest
@Preview(name = "Light", widthDp = 360, heightDp = 420)
@Preview(name = "Dark", widthDp = 360, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun TabBarSelectedStates() {
    Shot {
        val items = ShellTab.entries.map { OmnibusTabBarItem(stringResource(it.label), it.icon, it.selectedIcon) }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            repeat(items.size) { selected ->
                OmnibusTabBar(
                    items = items,
                    selectedIndex = selected,
                    onSelect = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
