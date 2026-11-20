package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.scan.ProviderRecord
import com.omnibus.omnibus.data.scan.ScanBook
import com.omnibus.omnibus.data.scan.ScanOutcome
import com.omnibus.omnibus.ui.components.FilledButton
import com.omnibus.omnibus.ui.components.InlineError
import com.omnibus.omnibus.ui.components.OmnibusTextField
import com.omnibus.omnibus.ui.components.ProviderCover
import com.omnibus.omnibus.ui.components.TextLinkButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The result of a lookup. A book outcome shows its card and the actions it allows. Any other outcome shows the "can't
 * place" message. Every action is disabled while a save is in flight, and the back arrow with it.
 */
@Composable
internal fun ScanOutcomeStage(
    outcome: ScanOutcome,
    note: String,
    writing: WritingAction?,
    writeFailed: Boolean,
    actions: ScanActions,
    wide: Boolean,
) {
    val palette = OmnibusTheme.palette
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = actions.onBack,
                enabled = writing == null,
                modifier = Modifier.size(48.dp).testTag(ScanTestTags.BACK),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.scan_back),
                    tint = palette.ink0,
                )
            }
            Text(
                text = stringResource(R.string.scan_outcome_title),
                style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0),
                modifier = Modifier.weight(1f),
            )
            ScanCloseButton(actions.onClose)
        }
        when (outcome) {
            is ScanOutcome.AlreadyOwned -> BookOutcome(outcome.book, outcome, note, writing, writeFailed, actions, wide)
            is ScanOutcome.OnWishlist -> BookOutcome(outcome.book, outcome, note, writing, writeFailed, actions, wide)
            is ScanOutcome.InLibraryUnowned -> BookOutcome(outcome.book, outcome, note, writing, writeFailed, actions, wide)
            is ScanOutcome.NotInLibrary -> ProviderOutcome(outcome.online, writing, writeFailed, actions, wide)
            ScanOutcome.Unplaced -> UnplacedOutcome(actions)
        }
    }
}

/** A library book's outcome: its card, its lines, the note when it is offered, and the actions. */
@Composable
private fun BookOutcome(
    book: ScanBook,
    outcome: ScanOutcome,
    note: String,
    writing: WritingAction?,
    writeFailed: Boolean,
    actions: ScanActions,
    wide: Boolean,
) {
    val palette = OmnibusTheme.palette
    val badge = when (outcome) {
        is ScanOutcome.InLibraryUnowned -> stringResource(R.string.scan_badge_in_library)
        is ScanOutcome.OnWishlist -> stringResource(R.string.scan_badge_on_wishlist)
        else -> stringResource(R.string.scan_badge_already_owned)
    }
    val badgeColor = if (outcome is ScanOutcome.AlreadyOwned) palette.ok else palette.accent
    val body: String? = when (outcome) {
        is ScanOutcome.InLibraryUnowned -> if (book.hasFiles) stringResource(R.string.scan_in_library_body) else null
        is ScanOutcome.OnWishlist -> stringResource(R.string.scan_on_wishlist_body)
        else -> stringResource(R.string.scan_already_owned_body)
    }
    OutcomeBody(
        card = { modifier, coverWidth ->
            ScanBookCard(
                cover = { ScanLibraryCover(book.uuid, book.title, book.authors, book.coverUrl != null) },
                badge = badge,
                badgeColor = badgeColor,
                title = book.title,
                author = book.authors.joinToString(", ").ifBlank { null },
                details = emptyList(),
                coverWidth = coverWidth,
                modifier = modifier,
            )
        },
        lines = {
            body?.let { Text(text = it, style = uiTextStyle(15, FontWeight.Normal, palette.ink2)) }
            if (outcome is ScanOutcome.InLibraryUnowned) {
                OmnibusTextField(
                    value = note,
                    onValueChange = actions.onNoteChanged,
                    placeholder = stringResource(R.string.scan_edition_note),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        writeFailed = writeFailed,
        actions = { OutcomeActions(book, outcome, writing, actions) },
        wide = wide,
    )
}

/**
 * A book the library doesn't have, as a provider describes it: the provider's card and its facts, then the two ways to
 * add it and Scan another. There is no note field.
 */
@Composable
private fun ProviderOutcome(
    record: ProviderRecord,
    writing: WritingAction?,
    writeFailed: Boolean,
    actions: ScanActions,
    wide: Boolean,
) {
    val palette = OmnibusTheme.palette
    val firstPublishedFormat = stringResource(R.string.scan_fact_first_published)
    val pagesFormat = stringResource(R.string.scan_fact_pages)
    val facts = providerFactParts(
        record,
        firstPublished = { firstPublishedFormat.format(it) },
        pages = { pagesFormat.format(it) },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
    OutcomeBody(
        card = { modifier, coverWidth ->
            ScanBookCard(
                cover = { ProviderCover(record.title, record.authors.firstOrNull(), record.coverUrl) },
                badge = stringResource(R.string.scan_badge_not_in_library),
                badgeColor = palette.warn,
                title = record.title,
                author = record.authors.joinToString(", ").ifBlank { null },
                details = listOfNotNull(record.series, facts),
                coverWidth = coverWidth,
                modifier = modifier,
            )
        },
        lines = {},
        writeFailed = writeFailed,
        actions = { ProviderActions(writing, actions) },
        wide = wide,
    )
}

/**
 * The shared outcome layout: the card with its save error, the lines and the actions. Two panes from [wide]: the card
 * on the left, the lines, error and actions on the right.
 */
@Composable
private fun OutcomeBody(
    card: @Composable (Modifier, Dp) -> Unit,
    lines: @Composable () -> Unit,
    writeFailed: Boolean,
    actions: @Composable () -> Unit,
    wide: Boolean,
) {
    val palette = OmnibusTheme.palette
    val error: @Composable () -> Unit = {
        InlineError(
            message = if (writeFailed) stringResource(R.string.scan_save_failed) else null,
            modifier = Modifier.testTag(ScanTestTags.WRITE_ERROR),
        )
    }
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxHeight().padding(horizontal = Spacing.screen), contentAlignment = Alignment.Center) {
                card(Modifier, 140.dp)
            }
            Box(Modifier.width(0.5.dp).fillMaxHeight().background(palette.line))
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = Spacing.screen),
                verticalArrangement = Arrangement.Center,
            ) {
                lines()
                error()
                Spacer(Modifier.height(Spacing.md))
                actions()
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.screen)) {
            card(Modifier.padding(top = Spacing.md), 92.dp)
            error()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.padding(top = Spacing.md)) {
                lines()
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
    }
}

/** The actions a library book's outcome allows, each disabled while a save is in flight. */
@Composable
private fun OutcomeActions(book: ScanBook, outcome: ScanOutcome, writing: WritingAction?, actions: ScanActions) {
    val idle = writing == null
    val saving = stringResource(R.string.scan_saving)
    Column(
        Modifier.fillMaxWidth().testTag(ScanTestTags.ACTIONS),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        when (outcome) {
            is ScanOutcome.InLibraryUnowned, is ScanOutcome.OnWishlist -> {
                FilledButton(
                    text = stringResource(R.string.scan_check_in),
                    onClick = actions.onCheckIn,
                    enabled = idle,
                    isLoading = writing == WritingAction.CheckIn,
                    loadingDescription = saving,
                )
                if (outcome is ScanOutcome.OnWishlist) {
                    FilledButton(
                        text = stringResource(R.string.scan_go_to_book_details),
                        onClick = { actions.onViewBook(book.uuid) },
                        enabled = idle,
                        prominent = false,
                    )
                }
            }

            is ScanOutcome.AlreadyOwned -> FilledButton(
                text = stringResource(R.string.scan_view_book),
                onClick = { actions.onViewBook(book.uuid) },
            )

            else -> Unit
        }
        ScanAnotherLink(actions.onScanAnother, idle)
    }
}

/** The actions a provider's outcome allows: the two ways to add the book, each with its own spinner, then Scan another. */
@Composable
private fun ProviderActions(writing: WritingAction?, actions: ScanActions) {
    val idle = writing == null
    val saving = stringResource(R.string.scan_saving)
    Column(
        Modifier.fillMaxWidth().testTag(ScanTestTags.ACTIONS),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledButton(
            text = stringResource(R.string.scan_add_physical),
            onClick = actions.onAddPhysical,
            enabled = idle,
            isLoading = writing == WritingAction.Physical,
            loadingDescription = saving,
        )
        FilledButton(
            text = stringResource(R.string.scan_add_to_wishlist),
            onClick = actions.onAddToWishlist,
            enabled = idle,
            isLoading = writing == WritingAction.Wishlist,
            loadingDescription = saving,
            prominent = false,
        )
        ScanAnotherLink(actions.onScanAnother, idle)
    }
}

/** The text button that returns to the camera, disabled while a save is in flight. */
@Composable
private fun ScanAnotherLink(onClick: () -> Unit, enabled: Boolean) {
    TextLinkButton(
        text = stringResource(R.string.scan_scan_another),
        color = OmnibusTheme.palette.accent,
        size = 15,
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth(),
    )
}

/** The "can't place" outcome: a centred message and Scan another, with no card. */
@Composable
private fun UnplacedOutcome(actions: ScanActions) {
    val palette = OmnibusTheme.palette
    Column(
        Modifier.fillMaxSize().padding(horizontal = Spacing.screen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(palette.bg2),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "?", style = displayTextStyle(30, palette.accent))
        }
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(R.string.scan_cant_place_title),
            style = displayTextStyle(24, palette.ink0),
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(ScanTestTags.TITLE),
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.scan_cant_place_body),
            style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.lg))
        FilledButton(text = stringResource(R.string.scan_scan_another), onClick = actions.onScanAnother)
    }
}
