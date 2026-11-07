package com.omnibus.omnibus.feature.shell.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.screenshot.Shot

@PreviewTest
@PreviewLightDark
@Composable
fun UnderConstructionSearch() {
    Shot {
        UnderConstructionScreen(UnderConstructionPage.Search)
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun UnderConstructionStats() {
    Shot {
        UnderConstructionScreen(UnderConstructionPage.Stats)
    }
}

@PreviewTest
@PreviewLightDark
@Composable
fun UnderConstructionYou() {
    Shot {
        UnderConstructionScreen(UnderConstructionPage.You)
    }
}
