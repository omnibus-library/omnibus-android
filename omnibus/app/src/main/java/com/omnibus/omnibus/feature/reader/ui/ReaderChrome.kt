package com.omnibus.omnibus.feature.reader.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.feature.reader.ReaderMenuRow
import com.omnibus.omnibus.ui.theme.Motion
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The context mode chrome: the top bar (back, title, bookmark toggle, more) and the flat dropdown under the more
 * button. It is drawn over the page and never resizes it. The page labels are drawn separately by [ReaderPageLabels].
 *
 * Stateless. The screen owns the state and passes plain values in.
 *
 * @param title The book's title, shown on one line in the bar.
 * @param chromeVisible Whether context mode is on. The bar and the dropdown are shown only then.
 * @param menuOpen Whether the dropdown is open.
 * @param menuRows The dropdown's rows, in order. Empty hides the more button.
 * @param colors The reading theme's chrome colours.
 * @param reduceMotion When true, every change appears on the next frame, with no transition.
 * @param insets The system bars and cutout, the same insets the page takes.
 * @param onClose Called when the back arrow is tapped.
 * @param onMenuOpened Called when the more button is tapped.
 * @param onMenuDismissed Called when a tap lands outside the open dropdown.
 * @param onMenuRowChosen Called with the row that was tapped.
 * @param pageBookmarked Whether the current page has a bookmark, which fills the bookmark toggle's icon.
 * @param canBookmark False when there is no current position, which disables the bookmark toggle.
 * @param onBookmarkToggled Called when the bookmark toggle is tapped.
 */
@Composable
internal fun ReaderChrome(
    title: String,
    chromeVisible: Boolean,
    menuOpen: Boolean,
    menuRows: List<ReaderMenuRow>,
    colors: ReaderChromeColors,
    reduceMotion: Boolean,
    insets: WindowInsets,
    onClose: () -> Unit,
    onMenuOpened: () -> Unit,
    onMenuDismissed: () -> Unit,
    onMenuRowChosen: (ReaderMenuRow) -> Unit,
    modifier: Modifier = Modifier,
    pageBookmarked: Boolean = false,
    canBookmark: Boolean = true,
    onBookmarkToggled: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        ChromeVisibility(
            visible = chromeVisible,
            reduceMotion = reduceMotion,
            enter = fadeIn(Motion.settle()),
            exit = fadeOut(Motion.settle()),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            ReaderContextBar(
                title = title,
                menuRows = menuRows,
                pageBookmarked = pageBookmarked,
                canBookmark = canBookmark,
                colors = colors,
                insets = insets,
                onClose = onClose,
                onMenuOpened = onMenuOpened,
                onBookmarkToggled = onBookmarkToggled,
            )
        }

        // Only while the dropdown is open. It is drawn over the bar, so a tap on the bar only dismisses the dropdown.
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
            visible = chromeVisible && menuOpen && menuRows.isNotEmpty(),
            reduceMotion = reduceMotion,
            enter = fadeIn(Motion.lift()),
            exit = fadeOut(Motion.snap()),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(top = ReaderContextMetrics.barHeight, end = ReaderContextMetrics.dropdownEndMargin),
        ) {
            ReaderContextMenu(rows = menuRows, colors = colors, onMenuRowChosen = onMenuRowChosen)
        }
    }
}

/**
 * The top bar: the back arrow, the title on one line, the bookmark toggle and, when there are rows, the more button.
 * It sits below the status bar and takes no surface of its own.
 */
@Composable
private fun ReaderContextBar(
    title: String,
    menuRows: List<ReaderMenuRow>,
    pageBookmarked: Boolean,
    canBookmark: Boolean,
    colors: ReaderChromeColors,
    insets: WindowInsets,
    onClose: () -> Unit,
    onMenuOpened: () -> Unit,
    onBookmarkToggled: () -> Unit,
) {
    // Read outside the modifier lambda, so the back arrow mirrors in a right-to-left layout.
    val mirror = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(ReaderContextMetrics.barHeight)
            .padding(horizontal = ReaderContextMetrics.barSidePadding)
            .testTag(ReaderChromeTags.BAR),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReaderChromeIconButton(
            iconRes = R.drawable.ic_book_detail_back,
            contentDescription = stringResource(R.string.reader_close_book),
            colors = colors,
            onClick = onClose,
            modifier = Modifier
                .testTag(ReaderChromeTags.BACK)
                .graphicsLayer { scaleX = mirror },
        )
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = ReaderContextMetrics.titleStartGap),
            style = uiTextStyle(ReaderContextMetrics.titleSize, FontWeight.Medium, colors.ink),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        ReaderChromeIconButton(
            iconRes = if (pageBookmarked) R.drawable.ic_reader_bookmark_filled else R.drawable.ic_reader_bookmark,
            contentDescription = stringResource(
                if (pageBookmarked) R.string.reader_bookmark_remove else R.string.reader_bookmark_add,
            ),
            colors = colors,
            onClick = onBookmarkToggled,
            enabled = canBookmark,
            modifier = Modifier.testTag(ReaderChromeTags.BOOKMARK),
        )
        if (menuRows.isNotEmpty()) {
            ReaderChromeIconButton(
                iconRes = R.drawable.ic_book_detail_more_dots,
                contentDescription = stringResource(R.string.reader_menu),
                colors = colors,
                onClick = onMenuOpened,
                modifier = Modifier.testTag(ReaderChromeTags.MORE),
            )
        }
    }
}

/**
 * The flat dropdown under the more button: the rows in a rounded plate with hairline dividers between them. It is as
 * wide as its widest row, and never narrower than [ReaderContextMetrics.dropdownMinWidth].
 */
@Composable
private fun ReaderContextMenu(
    rows: List<ReaderMenuRow>,
    colors: ReaderChromeColors,
    onMenuRowChosen: (ReaderMenuRow) -> Unit,
) {
    val shape = RoundedCornerShape(ReaderContextMetrics.dropdownCorner)
    Column(
        // The floor comes first, so it is not ignored by the intrinsic width.
        Modifier
            .widthIn(min = ReaderContextMetrics.dropdownMinWidth)
            .width(IntrinsicSize.Max)
            .shadow(ReaderContextMetrics.dropdownElevation, shape)
            .clip(shape)
            .background(colors.plate)
            .border(ReaderContextMetrics.hairline, colors.border, shape)
            .testTag(ReaderChromeTags.DROPDOWN),
    ) {
        rows.forEachIndexed { index, row ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ReaderContextMetrics.hairline)
                        .background(colors.border),
                )
            }
            ReaderContextMenuRow(row = row, colors = colors, onClick = { onMenuRowChosen(row) })
        }
    }
}

/** One dropdown row: its icon and label, in the chrome ink. */
@Composable
private fun ReaderContextMenuRow(
    row: ReaderMenuRow,
    colors: ReaderChromeColors,
    onClick: () -> Unit,
) {
    val (iconRes, titleRes) = when (row) {
        ReaderMenuRow.Contents -> R.drawable.ic_reader_contents to R.string.reader_menu_contents
        ReaderMenuRow.Bookmarks -> R.drawable.ic_reader_bookmark to R.string.reader_menu_bookmarks
        ReaderMenuRow.Settings -> R.drawable.ic_reader_settings to R.string.reader_menu_settings
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = ReaderContextMetrics.rowMinHeight)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(
                horizontal = ReaderContextMetrics.rowHorizontalPadding,
                vertical = ReaderContextMetrics.rowVerticalPadding,
            )
            .testTag(ReaderChromeTags.menuRow(row)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ReaderContextMetrics.rowIconLabelGap),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(ReaderContextMetrics.rowIconSize),
        )
        Text(
            text = stringResource(titleRes),
            style = uiTextStyle(ReaderContextMetrics.rowLabelSize, FontWeight.Normal, colors.ink),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The top and bottom page labels, drawn over the page in its bands. They take no touches, so a tap on them
 * reaches the page underneath.
 *
 * @param indicators The top and bottom labels.
 * @param colors The reading theme's chrome colours.
 * @param reduceMotion When true, a changed label appears on the next frame, with no crossfade.
 * @param insets The system bars and cutout, the same insets the page takes.
 */
@Composable
internal fun ReaderPageLabels(
    indicators: ReaderIndicators,
    colors: ReaderChromeColors,
    reduceMotion: Boolean,
    insets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
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
}

/** Metrics for the chrome's labels, ported from iOS `ReaderMenu`. */
internal object ReaderMenu {
    val buttonSize = 46.dp

    /** Wider than iOS's 72dp, so a full-width label can't reach a 46dp button 28dp from the edge. */
    val labelSidePadding = 80.dp
}

/**
 * Metrics for the context mode top bar and flat dropdown. The spec's figures, in dp, as designed.
 * The bar's layout row is the 48dp touch row, since the design's 36dp bar has no surface.
 */
internal object ReaderContextMetrics {
    val barHeight = 48.dp
    val barSidePadding = 4.dp
    val touchTarget = 48.dp
    val barIconSize = 22.dp
    val titleSize = 16.sp
    val titleStartGap = 4.dp

    val dropdownMinWidth = 150.dp
    val dropdownEndMargin = 12.dp
    val dropdownCorner = 8.dp
    val dropdownElevation = 8.dp
    val rowMinHeight = 32.dp
    val rowHorizontalPadding = 12.dp
    val rowVerticalPadding = 6.dp
    val rowIconSize = 18.dp
    val rowIconLabelGap = 10.dp
    val rowLabelSize = 14.sp

    val hairline = 0.5.dp

    /** The zoomed-out card's corner radius, on screen. */
    val cardCorner = 8.dp

    /** The gap between the card and each neighbour card beside it. */
    val pageGap = 12.dp

    /** The space between the top bar and the card's top, and between the card's bottom and the bottom inset. */
    val cardTopGap = 8.dp
    val cardBottomGap = 8.dp
}

/** Test tags on the chrome's nodes. */
internal object ReaderChromeTags {
    const val MENU_SCRIM = "reader_chrome_menu_scrim"
    const val LABEL_TOP = "reader_chrome_label_top"
    const val LABEL_BOTTOM = "reader_chrome_label_bottom"
    const val BOOKMARK = "reader_chrome_bookmark"
    const val BAR = "reader_chrome_bar"
    const val BACK = "reader_chrome_back"
    const val MORE = "reader_chrome_more"
    const val DROPDOWN = "reader_chrome_dropdown"

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
internal fun ChromeVisibility(
    visible: Boolean,
    reduceMotion: Boolean,
    enter: EnterTransition,
    exit: ExitTransition,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (reduceMotion) {
        if (visible) Box(modifier) { content() }
    } else {
        AnimatedVisibility(visible = visible, modifier = modifier, enter = enter, exit = exit) { content() }
    }
}

/**
 * A plain icon button for the top bar: a 48dp touch target around a 22dp icon in the chrome ink, with no plate or
 * border. A disabled button is drawn at 0.38 alpha and takes no taps.
 */
@Composable
internal fun ReaderChromeIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: String,
    colors: ReaderChromeColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(ReaderContextMetrics.touchTarget)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = colors.ink,
            modifier = Modifier.size(ReaderContextMetrics.barIconSize),
        )
    }
}
