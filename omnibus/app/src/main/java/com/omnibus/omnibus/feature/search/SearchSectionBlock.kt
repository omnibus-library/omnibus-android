package com.omnibus.omnibus.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnibus.omnibus.R
import com.omnibus.omnibus.ui.theme.OmnibusTheme
import com.omnibus.omnibus.ui.theme.Spacing
import com.omnibus.omnibus.ui.theme.monoTextStyle
import com.omnibus.omnibus.ui.theme.uiTextStyle
import java.util.Locale

/**
 * A section of the results: its uppercase label, announced as a heading, then its [content]. When [total] is more than
 * [shown], a "See all N" link at the end of the label's row calls [onSeeAll]. The link's 48dp target sets the row's height,
 * so the top padding shrinks with it to keep the label where it is.
 */
@Composable
internal fun SearchSectionBlock(
    kind: SearchSectionKind,
    total: Int,
    shown: Int,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = OmnibusTheme.palette
    val showsSeeAll = total > shown
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    start = Spacing.screen,
                    end = Spacing.screen,
                    top = if (showsSeeAll) 4.dp else 20.dp,
                    bottom = 10.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(kind.label).uppercase(Locale.ROOT),
                style = monoTextStyle(12, palette.ink2).copy(letterSpacing = 2.sp),
                modifier = Modifier.semantics { heading() },
            )
            if (showsSeeAll) {
                Spacer(Modifier.weight(1f))
                val link = pluralStringResource(kind.seeAllLink, total, total)
                Box(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onSeeAll)
                        .clearAndSetSemantics {
                            contentDescription = link
                            role = Role.Button
                        }
                        .padding(start = 12.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Text(
                        stringResource(R.string.search_see_all, total),
                        style = uiTextStyle(15, FontWeight.Medium, palette.accentText),
                    )
                }
            }
        }
        content()
    }
}
