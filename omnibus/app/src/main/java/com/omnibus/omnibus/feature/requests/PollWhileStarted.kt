package com.omnibus.omnibus.feature.requests

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** Runs [keepCurrent] while the screen is started, so a screen that polls stops in the background and starts again on return. */
@Composable
internal fun PollWhileStarted(keepCurrent: suspend () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.STARTED) { keepCurrent() } }
}
