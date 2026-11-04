package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [AudiobookController] for tests of the screens and the services around it. It records every command in call
 * order and never changes [state]: a test drives [state] directly.
 */
class FakeAudiobookController(initial: PlaybackState = PlaybackState()) : AudiobookController {

    /** Each command the controller was given, in call order. */
    sealed interface Command {
        data class Load(
            val bookUuid: String,
            val bookFileId: Long?,
            val positionSeconds: Double?,
            val autoplay: Boolean,
        ) : Command

        data object Play : Command

        data object Pause : Command

        data object TogglePlayPause : Command

        data object SkipBack : Command

        data object SkipForward : Command

        data object PreviousChapter : Command

        data object NextChapter : Command

        data class SeekTo(val bookSeconds: Double) : Command

        data class SetRate(val rate: Double, val persist: Boolean) : Command

        data class SetSleepTimer(val option: SleepTimerOption?) : Command

        data object Stop : Command
    }

    override val state: MutableStateFlow<PlaybackState> = MutableStateFlow(initial)

    /** Every command received, in call order. */
    val commands = mutableListOf<Command>()

    override fun load(bookUuid: String, bookFileId: Long?, positionSeconds: Double?, autoplay: Boolean) {
        commands += Command.Load(bookUuid, bookFileId, positionSeconds, autoplay)
    }

    override fun play() {
        commands += Command.Play
    }

    override fun pause() {
        commands += Command.Pause
    }

    override fun togglePlayPause() {
        commands += Command.TogglePlayPause
    }

    override fun skipBack() {
        commands += Command.SkipBack
    }

    override fun skipForward() {
        commands += Command.SkipForward
    }

    override fun previousChapter() {
        commands += Command.PreviousChapter
    }

    override fun nextChapter() {
        commands += Command.NextChapter
    }

    override fun seekTo(bookSeconds: Double) {
        commands += Command.SeekTo(bookSeconds)
    }

    override fun setRate(rate: Double, persist: Boolean) {
        commands += Command.SetRate(rate, persist)
    }

    override fun setSleepTimer(option: SleepTimerOption?) {
        commands += Command.SetSleepTimer(option)
    }

    override fun stop() {
        commands += Command.Stop
    }
}
