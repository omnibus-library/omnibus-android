package com.omnibus.omnibus.feature.reader.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.android.tools.screenshot.PreviewTest
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.feature.reader.ReaderBookState
import com.omnibus.omnibus.screenshot.Shot
import com.omnibus.omnibus.ui.components.CoverIdentity
import com.omnibus.omnibus.ui.theme.OmnibusTheme

private val RedCover = ReaderContentsPreviewCover.copy(accent = "oklch(0.55 0.2 25)")

/** The active segment, the current chapter's label and page, and its marker are red. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderContentsRedCover() {
    ContentsShot(RedCover)
}

/** The selected theme, typeface and margins and the justify switch are red, as is the steppers' glyph on dark. */
@PreviewTest
@PreviewLightDark
@Composable
fun ReaderSettingsRedCover() {
    SettingsShot(RedCover)
}

@Composable
private fun ContentsShot(cover: CoverIdentity) {
    Shot {
        ReaderContentsSheetContent(
            title = "The Long Road Home",
            cover = cover,
            accent = readerSheetAccent(ReaderBookState.Ready("The Long Road Home", cover), OmnibusTheme.palette),
            toc = ReaderContentsPreviewToc,
            location = ReaderContentsPreviewLocation,
            onEntryChosen = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun SettingsShot(cover: CoverIdentity) {
    Shot {
        ReaderSettingsSheetContent(
            settings = ReaderSettings(justify = true),
            accent = readerSheetAccent(ReaderBookState.Ready("The Long Road Home", cover), OmnibusTheme.palette),
            onSettingsChanged = {},
            onDone = {},
        )
    }
}
