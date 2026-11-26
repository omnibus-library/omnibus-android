package com.omnibus.omnibus.feature.libraries.remove

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Radius
import com.omnibus.omnibus.ui.theme.uiTextStyle

/** Test tags for removing a library. */
internal object RemoveLibraryTestTags {
    const val BUTTON = "remove-library-button"
    const val CONFIRM = "remove-library-confirm"
    const val CANCEL = "remove-library-cancel"
    const val TITLE = "remove-library-title"
}

/** The full-width outlined "Remove library" button on the detail screen, in the danger colour. */
@Composable
internal fun RemoveLibraryButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = OmnibusTheme.palette
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag(RemoveLibraryTestTags.BUTTON),
        border = BorderStroke(1.dp, palette.line),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = palette.badText),
        shape = RoundedCornerShape(Radius.lg),
    ) {
        Text(
            stringResource(R.string.libraries_remove_button),
            style = uiTextStyle(16, FontWeight.Medium, palette.badText),
        )
    }
}
