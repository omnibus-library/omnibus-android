package com.omnibus.omnibus.feature.bookdetail.actionbar

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.feature.bookdetail.BookDetailDestination
import com.omnibus.omnibus.ui.theme.rememberReducedMotion

/**
 * The action bar with its round buttons and the file chooser, without a ViewModel, so it can be hosted in tests. The
 * section supplies the model, the round actions and the colours. A Listen tap opens the player, or the chooser when
 * [roundActions] offers several files. Immersive read loads the audio through [onLoadAudio] before it opens the reader.
 *
 * @param roundActions The round buttons from [roundActions]. Empty hides them.
 * @param listenColors The Listen button's wash and rim.
 * @param chooserAccent The chooser's accent colour.
 * @param onLoadAudio Starts the audiobook on the given file, or the server's choice, for the immersive read.
 * @param reducedMotion Passed to the main button and the round buttons.
 */
@Composable
internal fun BookActionBarContent(
    bookUuid: String,
    model: ActionBarModel,
    roundActions: List<RoundAction>,
    colors: ActionBarColors,
    listenColors: ListenButtonColors,
    chooserAccent: Color,
    onNavigate: (BookDetailDestination) -> Unit,
    onLoadAudio: (audioFileId: Long?) -> Unit,
    modifier: Modifier = Modifier,
    reducedMotion: Boolean = rememberReducedMotion(),
) {
    // Only the flag is saved. The choices come from [roundActions] each time, so they are never stale.
    var choosing by rememberSaveable { mutableStateOf(false) }
    BookActionBar(
        model = model,
        colors = colors,
        onNavigate = onNavigate,
        modifier = modifier,
        reducedMotion = reducedMotion,
        extras = {
            RoundButtons(
                actions = roundActions,
                listenColors = listenColors,
                onRead = { onNavigate(it.destination) },
                onListen = { listen ->
                    when (val target = listen.target) {
                        is ListenTarget.Open -> onNavigate(target.destination)
                        is ListenTarget.Choose -> choosing = true
                    }
                },
                onImmersiveRead = { immersive ->
                    if (immersive.loadsAudio) onLoadAudio(immersive.audioFileId)
                    onNavigate(immersive.reader)
                },
                reducedMotion = reducedMotion,
            )
        },
    )
    val choices = (roundActions.firstOrNull { it is RoundAction.Listen } as? RoundAction.Listen)
        ?.target as? ListenTarget.Choose
    LaunchedEffect(choices) {
        if (choices == null) choosing = false
    }
    if (choosing && choices != null) {
        AudioFileChooserSheet(
            choices = choices.choices,
            accent = chooserAccent,
            onClose = { choosing = false },
            onPick = { onNavigate(BookDetailDestination.Player(bookUuid, it.id)) },
        )
    }
}
