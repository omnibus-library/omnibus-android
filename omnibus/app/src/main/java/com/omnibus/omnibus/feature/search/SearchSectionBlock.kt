package com.omnibus.omnibus.feature.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.monoTextStyle
import java.util.Locale

/**
 * A section of the results: its uppercase label, announced as a heading, then its [content]. Slice 2 adds the See all link
 * at the end of the label's row.
 */
@Composable
internal fun SearchSectionBlock(
    kind: SearchSectionKind,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = OmnibusTheme.palette
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, top = 20.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(kind.label).uppercase(Locale.ROOT),
                style = monoTextStyle(12, palette.ink2).copy(letterSpacing = 2.sp),
                modifier = Modifier.semantics { heading() },
            )
        }
        content()
    }
}
