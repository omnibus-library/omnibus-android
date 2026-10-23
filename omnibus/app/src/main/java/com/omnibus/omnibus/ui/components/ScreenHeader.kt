package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.displayTextStyle

/**
 * A screen's title block: a large display-face [title], with optional content above it (such as a
 * logo) and below it (such as a subtitle).
 *
 * @param leading Content shown above the title.
 * @param subtitle Content shown below the title. Callers style it, since its role varies by screen.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
) {
    val palette = OmnibusTheme.palette

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        leading?.invoke()
        Text(
            text = title,
            style = displayTextStyle(34, palette.ink0),
        )
        subtitle?.invoke()
    }
}
