package com.omnibus.omnibus.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.uiTextStyle

/**
 * A borderless text button for secondary actions, such as switching modes or changing servers.
 *
 * @param size Font size in sp.
 */
@Composable
fun TextLinkButton(
    text: String,
    color: Color,
    size: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onClick,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(horizontal = Spacing.xs),
        modifier = modifier,
    ) {
        Text(text = text, style = uiTextStyle(size, FontWeight.Normal, color))
    }
}
