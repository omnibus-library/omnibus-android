package com.omnibus.omnibus.data.audiobook.playback

import kotlinx.coroutines.CompletableDeferred

/**
 * Scriptable [AudioEngine] for controller tests. It records each call in [calls], keeps a [snapshot] the test can set,
 * and emits events and snapshots on demand.
 */
class TestAudioEngine : AudioEngine {

    /** Each call in order: `awaitReady`, `setSource`, `play`, `pause`, `stop`, `seekTo(...)`, `setSpeed(...)`. */
    val calls = mutableListOf<String>()

    /** Every source given to [setSource], in call order. */
    val sources = mutableListOf<AudioSource>()

    /** Every value given to [showNowPlaying], in call order. */
    val nowPlaying = mutableListOf<NowPlaying?>()

    /** When set, [awaitReady] holds until it completes. Null returns at once. */
    var ready: CompletableDeferred<Unit>? = null

    /** When set, [setSource] throws it. */
    var setSourceFailure: Throwable? = null

    /** When set, the other commands throw it. */
    var commandFailure: Throwable? = null

    /** When true, [play] and [pause] push the matching snapshot at once, as the real player does. */
    var reflect = true

    /** The state the fake reports. Tests may set it directly. */
    var snapshot = EngineSnapshot()

    private var listener: AudioEngine.Listener? = null

    override fun setListener(listener: AudioEngine.Listener?) {
        this.listener = listener
    }

    override suspend fun awaitReady() {
        calls += "awaitReady"
        ready?.await()
    }

    override suspend fun setSource(source: AudioSource) {
        calls += "setSource"
        sources += source
        setSourceFailure?.let { throw it }
        snapshot = EngineSnapshot(hasMedia = true)
    }

    override fun play() {
        calls += "play"
        commandFailure?.let { throw it }
        if (reflect) push(snapshot.copy(isPlaying = true, isAudible = true))
    }

    override fun pause() {
        calls += "pause"
        commandFailure?.let { throw it }
        if (reflect) push(snapshot.copy(isPlaying = false, isAudible = false))
    }

    override fun stop() {
        calls += "stop"
        commandFailure?.let { throw it }
        snapshot = EngineSnapshot()
    }

    override fun seekTo(bookSeconds: Double) {
        calls += "seekTo($bookSeconds)"
        commandFailure?.let { throw it }
        snapshot = snapshot.copy(positionSeconds = bookSeconds)
    }

    override fun setSpeed(speed: Double) {
        calls += "setSpeed($speed)"
        commandFailure?.let { throw it }
    }

    override fun snapshot(): EngineSnapshot = snapshot

    override fun showNowPlaying(nowPlaying: NowPlaying?) {
        this.nowPlaying += nowPlaying
    }

    /** Sends [event] to the listener, as the player would. */
    fun emit(event: EngineEvent) {
        listener?.onEvent(event)
    }

    /** Sends [snapshot] to the listener and makes it the current state. */
    fun push(snapshot: EngineSnapshot) {
        this.snapshot = snapshot
        listener?.onSnapshot(snapshot)
    }

    /** A failure: the event first, then the snapshot with playback stopped, which keeps the event-before-state order. */
    fun fail(kind: FailureKind) {
        emit(EngineEvent.Failed(kind))
        push(snapshot.copy(isPlaying = false, isAudible = false))
    }
}
