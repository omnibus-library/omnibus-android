package com.omnibus.omnibus.feature.reader.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The reader's chrome: the title and page labels, the close and menu buttons and the menu rows.
 * It is drawn over the page in the bands `#stage` reserves, and never resizes it.
 *
 * Stateless. The screen owns the state and passes plain values in.
 *
 * @param indicators The top and bottom labels.
 * @param chromeVisible Whether the buttons are shown. The labels stay on screen either way.
 * @param menuOpen Whether the menu rows are shown above the menu button.
 * @param menuRows The rows to show, in order. Empty hides the menu button.
 * @param percent The progress shown on the Contents row.
 * @param colors The reading theme's chrome colours.
 * @param reduceMotion When true, every change appears on the next frame, with no transition.
 * @param insets The system bars and cutout, the same insets the page takes.
 * @param onClose Called when the close button is tapped.
 * @param onMenuOpened Called when the menu button is tapped.
 * @param onMenuDismissed Called when a tap lands outside the open menu.
 * @param onMenuRowChosen Called with the row that was tapped.
 */
@Composable
internal fun ReaderChrome(
    indicators: ReaderIndicators,
    chromeVisible: Boolean,
    menuOpen: Boolean,
    menuRows: List<ReaderMenuRow>,
    percent: Int,
    colors: ReaderChromeColors,
    reduceMotion: Boolean,
    insets: WindowInsets,
    onClose: () -> Unit,
    onMenuOpened: () -> Unit,
    onMenuDismissed: () -> Unit,
    onMenuRowChosen: (ReaderMenuRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        // Labels take no touches, so a tap on them reaches the page underneath.
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(insets)
                .padding(vertical = Spacing.xs, horizontal = ReaderMenu.labelSidePadding),
        ) {
            ChromeLabel(
                text = indicators.top?.text(),
                tag = ReaderChromeTags.LABEL_TOP,
                colors = colors,
                reduceMotion = reduceMotion,
            )
            Spacer(Modifier.weight(1f))
            ChromeLabel(
                text = indicators.bottom?.text(),
                tag = ReaderChromeTags.LABEL_BOTTOM,
                colors = colors,
                reduceMotion = reduceMotion,
            )
        }

        // Only while the menu is open, so the page takes taps as usual otherwise.
        if (menuOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .testTag(ReaderChromeTags.MENU_SCRIM)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMenuDismissed,
                    ),
            )
        }

        ChromeVisibility(
            visible = chromeVisible,
            reduceMotion = reduceMotion,
            enter = fadeIn(Motion.settle()),
            exit = fadeOut(Motion.settle()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(insets)
                    .padding(horizontal = ReaderMenu.inset, vertical = Spacing.xs),
            ) {
                ReaderChromeButton(
                    iconRes = R.drawable.ic_reader_close,
                    contentDescription = stringResource(R.string.reader_close_book),
                    colors = colors,
                    onClick = onClose,
                    modifier = Modifier.align(Alignment.TopEnd).testTag(ReaderChromeTags.CLOSE),
                )
                if (menuRows.isNotEmpty()) {
                    Column(
                        Modifier.align(Alignment.BottomEnd),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(ReaderMenu.spacing),
                    ) {
                        ChromeVisibility(
                            visible = menuOpen,
                            reduceMotion = reduceMotion,
                            enter = fadeIn(Motion.lift()) +
                                slideInVertically(Motion.lift(IntOffset(1, 1))) { it / 2 },
                            exit = fadeOut(Motion.snap()),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(ReaderMenu.spacing),
                            ) {
                                menuRows.forEach { row ->
                                    ReaderMenuRowItem(
                                        row = row,
                                        percent = percent,
                                        colors = colors,
                                        onClick = { onMenuRowChosen(row) },
                                    )
                                }
                            }
                        }
                        ReaderChromeButton(
                            iconRes = R.drawable.ic_reader_menu,
                            contentDescription = stringResource(R.string.reader_menu),
                            colors = colors,
                            onClick = onMenuOpened,
                            modifier = Modifier.testTag(ReaderChromeTags.MENU),
                        )
                    }
                }
            }
        }
    }
}

/** Metrics for the chrome, ported from iOS `ReaderMenu`. */
internal object ReaderMenu {
    val buttonSize = 46.dp
    val rowHeight = 46.dp
    val rowMaxWidth = 272.dp
    val spacing = 8.dp
    val inset = 28.dp

    /** Wider than iOS's 72dp, so a full-width label can't reach a 46dp button 28dp from the edge. */
    val labelSidePadding = 80.dp
}

/** Test tags on the chrome's nodes. */
internal object ReaderChromeTags {
    const val CLOSE = "reader_chrome_close"
    const val MENU = "reader_chrome_menu"
    const val MENU_SCRIM = "reader_chrome_menu_scrim"
    const val LABEL_TOP = "reader_chrome_label_top"
    const val LABEL_BOTTOM = "reader_chrome_label_bottom"

    fun menuRow(row: ReaderMenuRow) = "reader_chrome_menu_row_${row.name}"
}

/** The string for this label, in the current locale. */
@Composable
internal fun ReaderLabel.text(): String = when (this) {
    is ReaderLabel.Title -> text
    is ReaderLabel.PagesLeftInChapter -> pluralStringResource(R.plurals.reader_pages_left_in_chapter, count, count)
    is ReaderLabel.ChapterOf -> stringResource(R.string.reader_chapter_of, chapter, total)
    is ReaderLabel.Page -> page.toString()
    is ReaderLabel.PageOf -> stringResource(R.string.reader_page_of, page, total)
    is ReaderLabel.Percent -> stringResource(R.string.reader_percent, pct)
}

/** One label, centred in its band. Shows nothing when [text] is null. */
@Composable
private fun ChromeLabel(
    text: String?,
    tag: String,
    colors: ReaderChromeColors,
    reduceMotion: Boolean,
) {
    Box(Modifier.fillMaxWidth().height(ReaderMenu.buttonSize), contentAlignment = Alignment.Center) {
        if (text != null) {
            Box(Modifier.testTag(tag).semantics(mergeDescendants = true) {}) {
                if (reduceMotion) {
                    LabelText(text, colors)
                } else {
                    Crossfade(targetState = text, animationSpec = Motion.settle(), label = tag) {
                        LabelText(it, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelText(text: String, colors: ReaderChromeColors) {
    Text(
        text = text,
        style = uiTextStyle(12.5.sp, FontWeight.Normal, colors.labelInk),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/**
 * Shows [content] while [visible] with [enter] and [exit], or, when [reduceMotion] is set, shows
 * and hides it at once.
 */
@Composable
private fun ChromeVisibility(
    visible: Boolean,
    reduceMotion: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    content: @Composable () -> Unit,
) {
    if (reduceMotion) {
        if (visible) content()
    } else {
        AnimatedVisibility(visible = visible, enter = enter, exit = exit) { content() }
    }
}

/** A 46dp circular button with an opaque plate, a hairline border and an icon in the chrome ink. */
@Composable
internal fun ReaderChromeButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    colors: ReaderChromeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(ReaderMenu.buttonSize)
            .clip(CircleShape)
            .background(colors.plate)
            .border(0.5.dp, colors.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** One menu row: an icon, its title and, for Contents, the current progress. */
@Composable
internal fun ReaderMenuRowItem(
    row: ReaderMenuRow,
    percent: Int,
    colors: ReaderChromeColors,
    onClick: () -> Unit,
) {
    val (iconRes, titleRes) = when (row) {
        ReaderMenuRow.Contents -> R.drawable.ic_reader_contents to R.string.reader_menu_contents
        ReaderMenuRow.Settings -> R.drawable.ic_reader_settings to R.string.reader_menu_settings
    }
    Row(
        Modifier
            .widthIn(max = ReaderMenu.rowMaxWidth)
            .fillMaxWidth()
            .height(ReaderMenu.rowHeight)
            .clip(CircleShape)
            .background(colors.plate)
            .border(0.5.dp, colors.border, CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp)
            .testTag(ReaderChromeTags.menuRow(row)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = stringResource(titleRes),
            modifier = Modifier.weight(1f),
            style = uiTextStyle(15.sp, FontWeight.Medium, colors.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (row == ReaderMenuRow.Contents) {
            Text(
                text = stringResource(R.string.reader_percent, percent),
                style = uiTextStyle(13.5.sp, FontWeight.Normal, colors.labelInk),
                maxLines = 1,
            )
        }
    }
}
