package com.omnibus.omnibus.feature.reader.ui

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import com.omnibus.omnibus.feature.quotecard.passageShareIntent
import com.omnibus.omnibus.feature.reader.HighlightMenuAction
import kotlinx.coroutines.launch

/**
 * The Android side effects of a menu action, which the screen runs before it forwards the action to the ViewModel. The
 * returned function takes the action and the menu target's [passage] text.
 */
@Composable
internal fun rememberMenuActionEffects(): (action: HighlightMenuAction, passage: String?) -> Unit {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, context, scope) {
        { action: HighlightMenuAction, passage: String? ->
            when (action) {
                HighlightMenuAction.Copy -> passage?.let { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, it))) } }
                // #330 Share
                HighlightMenuAction.Share -> passage?.let { context.startActivity(passageShareIntent(it)) }
                else -> Unit
            }
        }
    }
}
