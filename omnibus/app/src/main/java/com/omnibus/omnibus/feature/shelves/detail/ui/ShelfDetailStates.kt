package com.omnibus.omnibus.feature.shelves.detail.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.data.library.ShelfKind
import com.omnibus.omnibus.data.shelves.ShelvesError
import com.omnibus.omnibus.ui.components.StateActionButton
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** The widest a state's message runs before it wraps, so a long sentence stays readable on a tablet. */
private val MessageMaxWidth = 420.dp

/**
 * A state's message under the hero, left-aligned as the boards draw it: a kicker, a serif title and a line of text, with
 * an optional action below.
 *
 * @param hairline Whether a hairline divides the message from the hero above it.
 */
@Composable
internal fun ShelfMessage(
    kicker: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    hairline: Boolean = true,
    action: @Composable (() -> Unit)? = null,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth()) {
        if (hairline) Box(Modifier.fillMaxWidth().height(0.5.dp).background(palette.line))
        Column(Modifier.padding(horizontal = Spacing.screen).padding(top = 32.dp)) {
            Text(
                text = kicker.uppercase(),
                style = monoTextStyle(9.5.sp, palette.accent).copy(letterSpacing = 1.6.sp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = displayTextStyle(29, palette.ink0),
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = uiTextStyle(13.5.sp, FontWeight.Normal, palette.ink2),
                modifier = Modifier.widthIn(max = MessageMaxWidth),
            )
            if (action != null) {
                Box(Modifier.padding(top = 20.dp)) { action() }
            }
        }
    }
}

/**
 * The empty state of a shelf with no books, by its [kind]. A hand-picked shelf offers Add books when [canAddBooks].
 */
@Composable
internal fun ShelfEmptyMessage(
    kind: ShelfKind,
    canAddBooks: Boolean,
    onAddBooks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (kicker, title, message) = when (kind) {
        ShelfKind.Manual -> Triple(
            stringResource(R.string.shelf_detail_empty_kicker),
            stringResource(R.string.shelf_detail_empty_title),
            stringResource(R.string.shelf_detail_empty_message),
        )
        ShelfKind.Smart -> Triple(
            stringResource(R.string.shelf_detail_no_matches_kicker),
            stringResource(R.string.shelf_detail_no_matches_title),
            stringResource(R.string.shelf_detail_no_matches_message),
        )
        ShelfKind.Wishlist -> Triple(
            stringResource(R.string.shelf_detail_wishlist_kicker),
            stringResource(R.string.shelf_detail_wishlist_title),
            stringResource(R.string.shelf_detail_wishlist_message),
        )
    }
    ShelfMessage(
        kicker = kicker,
        title = title,
        message = message,
        modifier = modifier,
        action = if (kind == ShelfKind.Manual && canAddBooks) {
            { AddBooksButton(onAddBooks) }
        } else {
            null
        },
    )
}

/** The message for a shelf whose books have not reached this device: [count] books on the shelf, none cached. */
@Composable
internal fun ShelfUnreachableMessage(count: Long, modifier: Modifier = Modifier) {
    val n = count.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    ShelfMessage(
        kicker = stringResource(R.string.shelf_detail_unreachable_kicker),
        title = stringResource(R.string.shelf_detail_unreachable_title),
        message = pluralStringResource(R.plurals.shelf_detail_unreachable_message, n, n),
        modifier = modifier,
    )
}

/** A filter that matches no books: the shelf's page is empty under the facets. */
@Composable
internal fun ShelfFilteredEmptyMessage(onClearFacets: () -> Unit, modifier: Modifier = Modifier) {
    ShelfMessage(
        kicker = stringResource(R.string.shelf_detail_filtered_kicker),
        title = stringResource(R.string.shelf_detail_filtered_empty_title),
        message = stringResource(R.string.shelf_detail_filtered_empty_message),
        modifier = modifier,
        action = { StateActionButton(label = stringResource(R.string.shelf_detail_clear_filters), onClick = onClearFacets) },
    )
}

/** A filter the page could not apply: the connection copy when unreachable, else the failure's own text. */
@Composable
internal fun ShelfFilterFailedMessage(
    unreachable: Boolean,
    error: ShelvesError?,
    onClearFacets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ShelfMessage(
        kicker = stringResource(R.string.shelf_detail_filtered_kicker),
        title = stringResource(R.string.shelf_detail_filter_failed_title),
        message = if (unreachable) {
            stringResource(R.string.shelf_detail_filter_unreachable_message)
        } else {
            shelfErrorText(error)
        },
        modifier = modifier,
        action = { StateActionButton(label = stringResource(R.string.shelf_detail_clear_filters), onClick = onClearFacets) },
    )
}

/** The count line over a filtered page: "N of M books", where M is the shelf's book count. */
@Composable
internal fun CountLine(count: Int, total: Long, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    Text(
        text = stringResource(R.string.shelf_detail_count_line, count, total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).uppercase(),
        style = monoTextStyle(10.sp, palette.ink3).copy(letterSpacing = 0.8.sp),
        modifier = modifier.testTag(ShelfDetailTestTags.COUNT_LINE),
    )
}

/** The full-screen message for a shelf that was deleted or is no longer shared, with a way back to the shelves. */
@Composable
internal fun ShelfGoneMessage(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ShelfMessage(
        kicker = stringResource(R.string.shelf_detail_gone_kicker),
        title = stringResource(R.string.shelf_detail_gone_title),
        message = stringResource(R.string.shelf_detail_gone_message),
        modifier = modifier,
        hairline = false,
        action = {
            StateActionButton(
                label = stringResource(R.string.shelf_detail_gone_back),
                onClick = onBack,
            )
        },
    )
}

/** The Add books button of an empty hand-picked shelf: a compact accent pill with a plus, not a full-width button. */
@Composable
private fun AddBooksButton(onAddBooks: () -> Unit) {
    val palette = OmnibusTheme.palette
    Button(
        onClick = onAddBooks,
        colors = ButtonDefaults.buttonColors(containerColor = palette.accent, contentColor = palette.bg0),
        shape = CircleShape,
        modifier = Modifier.testTag(ShelfDetailTestTags.EMPTY_ADD_BOOKS),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_reader_plus),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(6.dp))
        Text(text = stringResource(R.string.shelf_detail_add_books))
    }
}
