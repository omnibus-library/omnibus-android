package com.omnibus.omnibus.feature.bookdetail.more.delete

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics
import com.omnibus.omnibus.R

/** The delete controls' custom semantics. A custom key, so accessibility services never see it. */
internal object DeleteSemantics {
    /** True on a control that deletes something, so tests can assert it. */
    val Destructive = SemanticsPropertyKey<Boolean>("DeleteDestructive")
}

/** Whether a control deletes something. See [DeleteSemantics.Destructive]. */
internal var SemanticsPropertyReceiver.destructive by DeleteSemantics.Destructive

/**
 * A clickable destructive control. It is a button whose click label is "delete permanently", so TalkBack says what the
 * double tap does. The semantics come before the click, so both land on the same merged node.
 */
@Composable
internal fun Modifier.destructiveClick(enabled: Boolean, onClick: () -> Unit): Modifier {
    val label = stringResource(R.string.book_detail_delete_a11y_destructive)
    return this
        .semantics { destructive = true }
        .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick)
}
