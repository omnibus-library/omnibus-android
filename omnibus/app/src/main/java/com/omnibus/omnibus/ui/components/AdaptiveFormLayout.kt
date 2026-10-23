package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.Spacing

/** Window width (Material "medium" breakpoint) at which unfolded foldables and tablets get the card layout. */
private val WIDE_LAYOUT_MIN_WIDTH = 600.dp

/** Maximum width of the card on wide windows. */
private val FORM_CARD_MAX_WIDTH = 480.dp

/**
 * Hosts a single-column form on the screen background, adapting to the window width.
 *
 * On standard widths the [content] fills the screen, top-aligned. Once the window reaches
 * [WIDE_LAYOUT_MIN_WIDTH] (an unfolded foldable or tablet) the [content] is capped at
 * [FORM_CARD_MAX_WIDTH] and centered inside a card that sits on top of the background.
 * [content] is laid out in a [Column] with `Spacing.xl` between children, so the same content
 * works in both layouts.
 *
 * @param modifier Applied to the background container; use it for screen-level concerns.
 * @param content The form, laid out in a [Column].
 */
@Composable
fun AdaptiveFormLayout(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(OmnibusTheme.palette.bg0)
            .then(modifier)
    ) {
        if (maxWidth >= WIDE_LAYOUT_MIN_WIDTH) {
            CardFormLayout(content = content)
        } else {
            CompactFormLayout(content = content)
        }
    }
}

/** Standard layout for phones: the form fills the screen width, top-aligned on the background. */
@Composable
private fun CompactFormLayout(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = 72.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.xl),
    ) {
        content()
        Spacer(modifier = Modifier.height(Spacing.xl))
    }
}

/** Wide layout: the form is capped in width and centered inside a card above the background. */
@Composable
private fun CardFormLayout(content: @Composable ColumnScope.() -> Unit) {
    val palette = OmnibusTheme.palette
    val shape = RoundedCornerShape(Radius.xl)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = FORM_CARD_MAX_WIDTH)
                .fillMaxWidth()
                .background(palette.bg1, shape)
                .border(0.5.dp, palette.line, shape)
                .padding(Spacing.xl + Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            content = content,
        )
    }
}
