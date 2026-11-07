package com.omnibus.omnibus.feature.bookdetail.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.omnibus.omnibus.feature.bookdetail.BookDetailSection

/**
 * A section that shows how many times it was tapped. The count is `rememberSaveable`, so a layout swap or a
 * recreation must keep it.
 */
fun saveableCounterSection(tag: String): BookDetailSection = BookDetailSection { _, modifier ->
    var count by rememberSaveable { mutableStateOf(0) }
    Text("$count", modifier = modifier.testTag(tag).clickable { count++ })
}

/** Counts the composition instances and disposals a section's remembered object goes through. */
class DisposeProbe {
    val instances = mutableListOf<Any>()
    var disposals = 0
}

/**
 * A section whose `remember`ed object must be the same instance across a layout swap. Nothing may dispose it: the probe
 * records a new instance only when the token is new, and a disposal whenever the section leaves composition.
 */
fun probeSection(tag: String, probe: DisposeProbe): BookDetailSection = BookDetailSection { _, modifier ->
    val token = remember { Any() }
    SideEffect { if (probe.instances.none { it === token }) probe.instances += token }
    DisposableEffect(Unit) { onDispose { probe.disposals++ } }
    Box(modifier.fillMaxWidth().height(24.dp).testTag(tag))
}

/**
 * A button that opens a modal sheet. The sheet's open flag and its text field are `rememberSaveable`, so a swap or a
 * recreation must keep the sheet open with its text.
 */
@OptIn(ExperimentalMaterial3Api::class)
fun sheetSection(openTag: String, fieldTag: String): BookDetailSection = BookDetailSection { _, modifier ->
    var open by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("") }
    Box(modifier.fillMaxWidth()) {
        Text("open", modifier = Modifier.testTag(openTag).clickable { open = true })
        if (open) {
            ModalBottomSheet(onDismissRequest = { open = false }) {
                TextField(value = text, onValueChange = { text = it }, modifier = Modifier.testTag(fieldTag))
            }
        }
    }
}
