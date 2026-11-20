package com.omnibus.omnibus.feature.scan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * The result of a lookup. A book outcome shows its card and the actions it allows. A provider's book shows its facts
 * and the ways to add it. A close match asks which library book is meant, and an unresolved ISBN offers a title search.
 * Every action is disabled while a save is in flight, and the back arrow with it.
 *
 * @param via How the ISBN in hand was found, which words a close match uses.
 * @param declined Whether the reader said a close match was a different book, which changes the add action's label.
 * @param wishlistedCandidates The close match's candidates that are on the wishlist.
 * @param titleDraft The inline title of an unresolved ISBN, kept while the title search is opened.
 */
@Composable
internal fun ScanOutcomeStage(
    outcome: ScanOutcome,
    note: String,
    writing: WritingAction?,
    writeFailed: Boolean,
    actions: ScanActions,
    wide: Boolean,
    via: FoundVia = FoundVia.Scan,
    declined: Boolean = false,
    wishlistedCandidates: Set<String> = emptySet(),
    titleDraft: String = "",
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
            is ScanOutcome.NotInLibrary -> ProviderOutcome(outcome.online, declined, writing, writeFailed, actions, wide)
            is ScanOutcome.CloseMatch -> CloseMatchOutcome(outcome, via, wishlistedCandidates, actions, wide)
            ScanOutcome.Unresolved -> UnresolvedOutcome(titleDraft, writing, actions, wide)
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
 * add it and Scan another. There is no note field. After "No, different book" the add action says it adds a new book.
 */
@Composable
private fun ProviderOutcome(
    record: ProviderRecord,
    declined: Boolean,
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
        actions = { ProviderActions(declined, writing, actions) },
        wide = wide,
    )
}

/**
 * A close match: the provider's book and the library books that share its title and author. One candidate asks whether
 * it is the book; several ask which one is. From [wide] the left pane says what it is and the right pane does the
 * asking, with a list's own buttons beside each candidate.
 */
@Composable
private fun CloseMatchOutcome(
    outcome: ScanOutcome.CloseMatch,
    via: FoundVia,
    wishlistedCandidates: Set<String>,
    actions: ScanActions,
    wide: Boolean,
) {
    val palette = OmnibusTheme.palette
    val several = outcome.candidates.size > 1
    val copy = closeMatchCopy(via, several)
    val subject = stringResource(copy.subject)
    val heading: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                text = stringResource(copy.heading),
                style = displayTextStyle(26, palette.ink0),
                modifier = Modifier
                    .testTag(ScanTestTags.TITLE)
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
            )
            Text(text = stringResource(copy.subtitle, subject), style = uiTextStyle(15, FontWeight.Normal, palette.ink2))
        }
    }
    val found: @Composable () -> Unit = {
        if (!several) FoundCard(outcome.scanned, stringResource(copy.found))
    }
    val candidates: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            outcome.candidates.forEachIndexed { index, book ->
                CandidateCard(book, wishlisted = book.uuid in wishlistedCandidates)
                if (several) {
                    FilledButton(
                        text = stringResource(copy.pick),
                        onClick = { actions.onCandidatePicked(book) },
                        prominent = index == 0,
                    )
                }
            }
        }
    }
    val answers: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().testTag(ScanTestTags.ACTIONS), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (!several) {
                FilledButton(
                    text = stringResource(copy.pick),
                    onClick = { actions.onCandidatePicked(outcome.candidates.first()) },
                )
            }
            FilledButton(
                text = stringResource(R.string.scan_already_have),
                onClick = actions.onAlreadyHave,
                prominent = false,
            )
            TextLinkButton(
                text = stringResource(copy.decline),
                color = palette.accent,
                size = 15,
                onClick = actions.onCloseMatchDeclined,
                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth(),
            )
        }
    }
    OutcomeSplit(
        wide = wide,
        leading = {
            heading()
            found()
            if (!several) candidates()
        },
        trailing = {
            if (several) candidates()
            answers()
        },
    )
}

/** A close match's candidate: a library book, badged as a possible match, or as on the wishlist with what checking it in does. */
@Composable
private fun CandidateCard(book: ScanBook, wishlisted: Boolean) {
    val palette = OmnibusTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        ScanBookCard(
            cover = { ScanLibraryCover(book.uuid, book.title, book.authors, book.coverUrl != null) },
            badge = stringResource(if (wishlisted) R.string.scan_badge_on_wishlist else R.string.scan_badge_possible_match),
            badgeColor = if (wishlisted) palette.accent else palette.warn,
            title = book.title,
            author = book.authors.joinToString(", ").ifBlank { null },
            details = emptyList(),
            coverWidth = CANDIDATE_COVER_WIDTH,
            titleIsHeading = false,
        )
        if (wishlisted) {
            Text(text = stringResource(R.string.scan_on_wishlist_body), style = uiTextStyle(15, FontWeight.Normal, palette.ink2))
        }
    }
}

/** The scanned record of a single close match, with the word for how the ISBN was found. */
@Composable
private fun FoundCard(record: ProviderRecord, label: String) {
    val palette = OmnibusTheme.palette
    val year = record.firstPublishYear?.toString() ?: record.year?.takeIf { it.isNotBlank() }
    val facts = listOfNotNull(record.authors.firstOrNull(), year).joinToString(" · ")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(0.5.dp, palette.line, RoundedCornerShape(Radius.md))
            .padding(Spacing.md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.width(FOUND_COVER_WIDTH)) {
            ProviderCover(record.title, record.authors.firstOrNull(), record.coverUrl)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(text = label, style = monoTextStyle(11, palette.ink2))
            Text(text = record.title, style = uiTextStyle(16, FontWeight.SemiBold, palette.ink0))
            if (facts.isNotEmpty()) {
                Text(text = facts, style = uiTextStyle(14, FontWeight.Normal, palette.ink2))
            }
        }
    }
}

/**
 * An unresolved ISBN: a "?" chip, the title and body, then an inline title to search for and the actions. From [wide]
 * the message is on the left and the search and actions on the right.
 */
@Composable
private fun UnresolvedOutcome(titleDraft: String, writing: WritingAction?, actions: ScanActions, wide: Boolean) {
    val palette = OmnibusTheme.palette
    OutcomeSplit(
        wide = wide,
        leading = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(palette.bg2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "?", style = displayTextStyle(30, palette.accent))
                }
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.scan_unresolved_title),
                    style = displayTextStyle(24, palette.ink0),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .testTag(ScanTestTags.TITLE)
                        .semantics {
                            heading()
                            liveRegion = LiveRegionMode.Polite
                        },
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.scan_unresolved_body),
                    style = uiTextStyle(15, FontWeight.Normal, palette.ink2),
                    textAlign = TextAlign.Center,
                )
            }
        },
        trailing = {
            Column(
                Modifier.fillMaxWidth().testTag(ScanTestTags.ACTIONS),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                OmnibusTextField(
                    value = titleDraft,
                    onValueChange = actions.onTitleQueryChanged,
                    placeholder = stringResource(R.string.scan_unresolved_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { actions.onSearchByTitle() }),
                )
                FilledButton(text = stringResource(R.string.scan_search_by_title), onClick = actions.onSearchByTitle)
                FilledButton(
                    text = stringResource(R.string.scan_already_have),
                    onClick = actions.onAlreadyHave,
                    prominent = false,
                )
                ScanAnotherLink(actions.onScanAnother, writing == null)
            }
        },
    )
}

/**
 * The two-part layout of the recovery outcomes: one column on a narrow window, and from [wide] two panes divided by a
 * hairline. Each part scrolls, so a long list of candidates stays reachable.
 */
@Composable
private fun OutcomeSplit(wide: Boolean, leading: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    val palette = OmnibusTheme.palette
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                leading()
            }
            Box(Modifier.width(0.5.dp).fillMaxHeight().background(palette.line))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                trailing()
            }
        }
    } else {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            leading()
            trailing()
        }
    }
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

/**
 * The actions a provider's outcome allows: the two ways to add the book, each with its own spinner, the way to say the
 * book is already owned, then Scan another. The primary add reads "Add as a new physical book" after "No, different book".
 */
@Composable
private fun ProviderActions(declined: Boolean, writing: WritingAction?, actions: ScanActions) {
    val idle = writing == null
    val saving = stringResource(R.string.scan_saving)
    Column(
        Modifier.fillMaxWidth().testTag(ScanTestTags.ACTIONS),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilledButton(
            text = stringResource(if (declined) R.string.scan_add_new_physical else R.string.scan_add_physical),
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
        FilledButton(
            text = stringResource(R.string.scan_already_have),
            onClick = actions.onAlreadyHave,
            enabled = idle,
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

private val CANDIDATE_COVER_WIDTH = 72.dp
private val FOUND_COVER_WIDTH = 48.dp
