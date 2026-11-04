package com.omnibus.omnibus.data.audiobook.playback

import android.util.Log
import com.omnibus.omnibus.data.audiobook.AudiobookManifest
import com.omnibus.omnibus.data.audiobook.AudiobookManifestError
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.audiobook.AudiobookRepository
import com.omnibus.omnibus.data.audiobook.chooseAudioFileId
import com.omnibus.omnibus.data.audiobook.remote.RecentListeningSource
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.BookAvailability
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.network.ConnectivityMonitor
import com.omnibus.omnibus.di.ApplicationScope
import com.omnibus.omnibus.di.TimeSource
import java.util.Optional
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The audiobook player (C2): one instance per app. It loads books, folds the engine's events into [PlaybackState], and
 * reports to every [PlaybackListener]. Playback internals; not for UI use beyond [AudiobookController].
 *
 * The controller owns the load pipeline, the rebuild after a failure, the artwork retry and the "play with nothing
 * loaded" pick. Commands run on the main thread and mutate state at once. Only the load, the rebuild and the connection
 * wait run as coroutines, so a [pause] during a manifest fetch takes effect immediately. Every await checks the
 * generation, so a superseded load returns silently.
 *
 * The seams are optional. With no [ResumeStartResolver] a load starts at `positionSeconds ?: 0.0`, with no
 * [PlaybackRateStore] the rate is 1.0, and with no [SleepTimer] the timer does nothing.
 */
@Singleton
class DefaultAudiobookController @Inject constructor(
    private val engine: AudioEngine,
    private val repository: AudiobookRepository,
    private val libraryRepository: LibraryRepository,
    private val recentListening: RecentListeningSource,
    private val bookAvailability: BookAvailability,
    private val artworkSource: CoverArtworkSource,
    private val connectivity: ConnectivityMonitor,
    private val appBackground: AppBackgroundSignal,
    resumeStartResolver: Optional<ResumeStartResolver>,
    playbackRateStore: Optional<PlaybackRateStore>,
    sleepTimer: Optional<SleepTimer>,
    listeners: Set<@JvmSuppressWildcards PlaybackListener>,
    private val timeSource: TimeSource,
    @param:ApplicationScope private val scope: CoroutineScope,
) : AudiobookController, AudioEngine.Listener {

    private data class LoadRequest(
        val bookUuid: String,
        val bookFileId: Long?,
        val positionSeconds: Double?,
        val autoplay: Boolean,
    )

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()
    private val resolver = resumeStartResolver.orElse(null)
    private val rateStore = playbackRateStore.orElse(null)
    private val timer = sleepTimer.orElse(null)
    private val dispatcher = ListenerDispatcher(listeners) { Log.w(TAG, "A playback listener failed", it) }
    private val health = PlaybackHealth()
    private val artworkRetry = ArtworkRetrySchedule()

    private var generation = 0
    private var loadJob: Job? = null
    private var pendingResume: PendingResume? = null
    private var readyGate: CompletableDeferred<Double>? = null
    private var loadReachedReady = false
    private var lastRequest: LoadRequest? = null
    private var loadFailed = false
    private var source: AudioSource? = null
    private var autoStartOwed: Int? = null          // the generation whose load owes a start
    private var recoveryJob: Job? = null
    private var resumeAfterRecovery = false
    private var rebuildQuiet = false                // a rebuild's own stop and restart are not onPause/onPlay
    private var quietStop = false                   // the next stop is an end, an unload or a reset, not onPause
    private var reportedPlaying = false
    private var tickJob: Job? = null
    private var rateTouched = false
    private var engineReady = false
    private var engineLost = false
    private var connectJob: Job? = null
    private val queuedEngineCalls = ArrayDeque<() -> Unit>()
    private var snapshot = EngineSnapshot()
    private var recentJob: Job? = null
    private var recentOwed = false
    private var image: NowPlayingImage = NowPlayingImage.None
    private var artworkFailed = false
    private var artworkJob: Job? = null
    private var lastNowPlaying: NowPlaying? = null

    init {
        engine.setListener(this)
        timer?.let { t -> scope.launch { t.state.collect { s -> _state.update { it.copy(sleepTimer = s) } } } }
        scope.launch { appBackground.backgrounded.collect { _state.value.takeIf { it.book != null }?.let(dispatcher::appBackground) } }
        scope.launch {
            var wasOnline = connectivity.isOnline.value
            connectivity.isOnline.collect { online ->
                if (online && !wasOnline) { artworkRetry.noteConnectionReturned(); if (artworkFailed) loadArtwork() }
                wasOnline = online
            }
        }
    }

    // ---- load ----
    override fun load(bookUuid: String, bookFileId: Long?, positionSeconds: Double?, autoplay: Boolean) {
        val current = _state.value
        val loaded = current.book
        val request = LoadRequest(bookUuid, bookFileId, positionSeconds, autoplay)
        val sameItem = loaded != null && loaded.bookUuid == bookUuid &&
            (bookFileId == null || bookFileId == loaded.bookFileId || bookFileId == lastRequest?.bookFileId) &&
            current.error == null && !loadFailed && !engineLost
        if (sameItem) {
            if (positionSeconds != null) {
                if (current.isLoading) { startLoad(request, previous = null); return }
                seekTo(positionSeconds)
            }
            if (autoplay && !current.isPlaying) play()
            return
        }
        startLoad(request, previous = current.takeIf { loaded != null && loaded.bookUuid != bookUuid })
    }

    private fun startLoad(request: LoadRequest, previous: PlaybackState?) {
        abandonLoad()
        val gen = ++generation
        lastRequest = request; loadFailed = false; engineLost = false; source = null
        loadReachedReady = false; rateTouched = false; health.reset()
        autoStartOwed = if (request.autoplay) gen else null
        rebuildQuiet = false; quietStop = true
        image = NowPlayingImage.None; artworkFailed = false; lastNowPlaying = null
        _state.value = PlaybackState(
            book = PlayingBook(request.bookUuid, request.bookFileId ?: 0L, "", null, false),
            isLoading = true,
            sleepTimer = _state.value.sleepTimer,
        )
        withEngine { engine.pause(); engine.setSpeed(1.0) }
        refreshPlaying()
        loadJob = scope.launch { runLoad(gen, request, previous) }
    }

    private fun abandonLoad() {
        loadJob?.cancel(); loadJob = null
        recoveryJob?.cancel(); recoveryJob = null; resumeAfterRecovery = false
        pendingResume?.cancel(); pendingResume = null
        readyGate?.cancel(); readyGate = null
        artworkJob?.cancel(); artworkJob = null
        recentJob?.cancel(); recentJob = null; recentOwed = false
        stopTicking()
    }

    private suspend fun runLoad(gen: Int, request: LoadRequest, previous: PlaybackState?) {
        if (previous != null) {
            dispatcher.unload(previous, UnloadReason.SwitchBook)
            timer?.set(null)
            if (gen != generation) return
        }
        val pending = resolver?.begin(request.bookUuid, request.bookFileId, request.positionSeconds)
        pendingResume = pending
        readRate(gen, request.bookUuid)
        val (book, chosen, result) = coroutineScope {
            val book = async { readBook(request.bookUuid) }
            val manifest = async {
                val chosen = chooseAudioFileId(request.bookFileId, pending?.savedFileId())
                chosen to repository.getManifest(request.bookUuid, chosen)
            }
            val (chosen, result) = manifest.await()
            Triple(book.await(), chosen, result)
        }
        if (gen != generation) return
        val manifest = when (result) {
            is AudiobookManifestResult.Success -> result.manifest
            is AudiobookManifestResult.Failure -> return failLoad(gen, result.error.toPlaybackError())
        }
        val plan = planAudioSource(request.bookUuid, manifest)
        if (plan is SourcePlan.Failed) return failLoad(gen, plan.error)
        val ready = plan as SourcePlan.Ready
        val loadedFileId = manifest.bookFileId.takeIf { it > 0 } ?: chosen ?: book?.firstAudioFileId() ?: 0L
        val direct = manifest as? AudiobookManifest.Direct
        _state.update {
            it.copy(
                book = PlayingBook(request.bookUuid, loadedFileId, book?.displayTitle.orEmpty(), book?.authorOrNull(), book?.hasEpub == true),
                chapters = direct?.chapters.orEmpty(),
                durationSeconds = direct?.totalDurationSeconds ?: 0.0,
            )
        }
        val gate = CompletableDeferred<Double>().also { readyGate = it }
        try {
            awaitEngine()
            if (gen != generation) return
            engine.setSource(ready.source)
        } catch (e: CancellationException) { throw e } catch (t: Throwable) {
            Log.w(TAG, "Couldn't build the audio source", t)
            return failLoad(gen, PlaybackError.TimelineFailed)
        }
        if (gen != generation) return
        source = ready.source
        pushNowPlaying()
        loadArtwork()
        val duration = direct?.totalDurationSeconds ?: gate.await().also { d -> _state.update { it.copy(durationSeconds = d) } }
        if (gen != generation) return
        val answer = if (pending != null) pending.settle(loadedFileId, duration) else request.positionSeconds
        if (gen != generation) return
        pendingResume = null
        val start = answer?.takeIf { it > 0 } ?: 0.0
        if (start > 0) seekEngineTo(clampToBook(start, duration))
        _state.update { it.copy(isLoading = false) }
        refreshPlaying()
        dispatcher.settled(_state.value, start)
        if (autoStartOwed == gen) { autoStartOwed = null; withEngine { engine.play() } }
        refreshPlaying()
    }

    private fun failLoad(gen: Int, error: PlaybackError) {
        if (gen != generation) return
        pendingResume?.cancel(); pendingResume = null
        readyGate?.cancel(); readyGate = null
        autoStartOwed = null; resumeAfterRecovery = false; recoveryJob?.cancel()
        loadFailed = true; quietStop = true
        _state.update { it.copy(isLoading = false, isBuffering = false, error = error) }
        withEngine { engine.pause() }
        refreshPlaying()
    }

    // ---- transport ----
    override fun play() {
        artworkRetry.noteResumed()
        if (artworkFailed) loadArtwork()
        val s = _state.value
        val request = lastRequest
        when {
            s.book == null -> resumeMostRecent()
            s.isLoading -> autoStartOwed = generation
            loadFailed && request != null -> startLoad(request.copy(autoplay = true), previous = null)
            s.error == PlaybackError.StoppedAfterRetries || engineLost -> {
                health.reset(); engineLost = false
                _state.update { it.copy(error = null) }
                resumeAfterRecovery = true
                scheduleRebuild(attempt = 1)
            }
            recoveryJob?.isActive == true -> resumeAfterRecovery = true
            else -> withEngine { engine.play() }
        }
        refreshPlaying()
    }

    override fun pause() {
        val showedPlaying = _state.value.isPlaying
        autoStartOwed = null; resumeAfterRecovery = false
        recentOwed = false; recentJob?.cancel(); recentJob = null
        if (rebuildQuiet) {
            rebuildQuiet = false
            if (showedPlaying && !reportedPlaying) dispatcher.pause(_state.value.copy(isPlaying = false))
        }
        if (_state.value.book != null) withEngine { engine.pause() }
        refreshPlaying()
    }

    override fun togglePlayPause() = if (_state.value.isPlaying) pause() else play()
    override fun skipBack() = skipBy(-SKIP_BACK_SECONDS)
    override fun skipForward() = skipBy(SKIP_FORWARD_SECONDS)
    override fun previousChapter() { if (canSeek()) previousChapterTarget(_state.value.chapters, currentPosition())?.let(::seekEngineTo) }
    override fun nextChapter() { if (canSeek()) nextChapterTarget(_state.value.chapters, currentPosition())?.let(::seekEngineTo) }
    override fun seekTo(bookSeconds: Double) {
        if (!canSeek() || !bookSeconds.isFinite()) return
        seekEngineTo(clampToBook(bookSeconds, _state.value.durationSeconds))
    }
    private fun canSeek() = _state.value.book != null && !_state.value.isLoading
    private fun skipBy(delta: Double) {
        if (!canSeek()) return
        seekEngineTo(skipTarget(currentPosition(), _state.value.durationSeconds, delta))
    }
    private fun currentPosition(): Double =
        if (engineReady && snapshot.hasMedia) engine.snapshot().positionSeconds else _state.value.positionSeconds
    private fun seekEngineTo(target: Double) {
        _state.update { it.withPosition(target) }
        withEngine { engine.seekTo(target) }
        pushNowPlaying()
    }

    override fun setRate(rate: Double, persist: Boolean) {
        val clamped = clampRate(rate) ?: return
        rateTouched = true
        applyRate(clamped)
        val uuid = _state.value.book?.bookUuid ?: return
        if (persist) rateStore?.let { store -> scope.launch { runCatching { store.save(uuid, clamped) } } }
    }
    private fun applyRate(rate: Double) { _state.update { it.copy(rate = rate) }; withEngine { engine.setSpeed(rate) } }
    private fun readRate(gen: Int, uuid: String) {
        val store = rateStore ?: return
        scope.launch {
            val answer = try { store.rate(uuid) } catch (e: CancellationException) { throw e } catch (t: Throwable) { null }
            if (gen != generation || rateTouched) return@launch
            answer?.let(::clampRate)?.let(::applyRate)
        }
    }

    override fun setSleepTimer(option: SleepTimerOption?) { timer?.set(option) }

    override fun stop() {
        val s = _state.value
        abandonLoad()
        val gen = ++generation
        autoStartOwed = null; rebuildQuiet = false
        if (s.book == null) { refreshPlaying(); return }
        quietStop = true
        withEngine { engine.pause() }
        refreshPlaying()
        scope.launch {
            dispatcher.unload(s, UnloadReason.Stop)
            if (gen != generation) return@launch
            withEngine { engine.stop(); engine.showNowPlaying(null) }
            timer?.set(null)
            source = null; lastRequest = null; loadFailed = false; engineLost = false; health.reset()
            image = NowPlayingImage.None; artworkFailed = false; lastNowPlaying = null
            _state.value = PlaybackState(sleepTimer = timer?.state?.value)
            refreshPlaying()
        }
    }

    // ---- I-SYS-5 ----
    private fun resumeMostRecent() {
        recentOwed = true
        if (recentJob?.isActive == true) return
        recentJob = scope.launch {
            val points = try { recentListening.recentAudio() } catch (e: CancellationException) { throw e } catch (t: Throwable) { emptyList() }
            val pick = resumeCandidate(points) { uuid -> bookAvailability.canOpen(uuid, ProgressFormat.Listening) }
            val owed = recentOwed
            recentOwed = false
            recentJob = null // load() below calls abandonLoad(), which would otherwise cancel this job mid-call
            if (owed && pick != null && _state.value.book == null) {
                load(pick.record.bookUuid, pick.record.bookFileId, if (resolver == null) pick.record.audioPositionSeconds else null, autoplay = true)
            } else refreshPlaying()
        }
    }

    // ---- health ----
    private fun scheduleRebuild(attempt: Int) {
        val src = source ?: return
        recoveryJob?.cancel()
        val gen = generation
        val at = _state.value.positionSeconds
        recoveryJob = scope.launch {
            delay(attempt * 1_000L)
            if (gen != generation) return@launch
            try { awaitEngine(); engine.setSource(src); engine.seekTo(at) }
            catch (e: CancellationException) { throw e }
            catch (t: Throwable) {
                Log.w(TAG, "Couldn't rebuild the audio source", t)
                resumeAfterRecovery = false; rebuildQuiet = false; loadFailed = true
                _state.update { it.copy(error = PlaybackError.TimelineFailed) }
                refreshPlaying(); return@launch
            }
            if (gen != generation) return@launch
            if (resumeAfterRecovery) { resumeAfterRecovery = false; withEngine { engine.play() } } else rebuildQuiet = false
            refreshPlaying()
        }
        refreshPlaying()
    }

    // ---- engine callbacks (AudioEngine.Listener; events always arrive before the snapshot of the same change) ----
    override fun onEvent(event: EngineEvent) {
        when (event) {
            EngineEvent.Ready -> { loadReachedReady = true; readyGate?.complete(engine.snapshot().durationSeconds ?: 0.0) }
            is EngineEvent.Failed -> onEngineFailure(event.kind)
            EngineEvent.NoAudioTrack -> { loadJob?.cancel(); failLoad(generation, PlaybackError.PartUnreadable) }
            EngineEvent.Ended -> {
                quietStop = true
                _state.update { it.withPosition(it.durationSeconds).copy(isPlaying = false) }
                dispatcher.ended(_state.value)
            }
            EngineEvent.PausedExternally -> { autoStartOwed = null; resumeAfterRecovery = false; recentOwed = false; refreshPlaying() }
            EngineEvent.Disconnected -> {
                engineReady = false
                if (_state.value.book != null && !_state.value.isLoading) engineLost = true
                refreshPlaying()
            }
        }
    }

    private fun onEngineFailure(kind: FailureKind) {
        val s = _state.value
        if (s.book == null) return
        if (kind == FailureKind.Unreadable && !loadReachedReady) { loadJob?.cancel(); failLoad(generation, PlaybackError.PartUnreadable); return }
        when (val action = health.observed(PlaybackSignal.Failed)) {
            is HealthAction.Rebuild -> { resumeAfterRecovery = resumeAfterRecovery || s.isPlaying; rebuildQuiet = true; scheduleRebuild(action.attempt) }
            HealthAction.Surrender -> {
                resumeAfterRecovery = false; recoveryJob?.cancel(); rebuildQuiet = false
                _state.update { it.copy(error = PlaybackError.StoppedAfterRetries, isBuffering = false) }
            }
            else -> Unit
        }
        refreshPlaying()
    }

    override fun onSnapshot(snapshot: EngineSnapshot) {
        this.snapshot = snapshot
        if (snapshot.isAudible) health.observed(PlaybackSignal.Playing)
        if (snapshot.isBuffering) health.observed(PlaybackSignal.Starved)
        val s = _state.value
        if (s.book != null && !s.isLoading && snapshot.hasMedia) {
            _state.update { it.withPosition(snapshot.positionSeconds) }
            pushNowPlaying()
        }
        refreshPlaying()
    }

    /** Derives isPlaying and isBuffering, reports onPlay and onPause, and runs the tick. */
    private fun refreshPlaying() {
        val pendingStart = autoStartOwed == generation || (resumeAfterRecovery && recoveryJob?.isActive == true) || recentOwed
        _state.update {
            it.copy(
                isPlaying = (snapshot.isPlaying && it.error == null && it.book != null) || pendingStart,
                isBuffering = snapshot.isBuffering && snapshot.isPlaying && it.error == null && it.book != null,
            )
        }
        val s = _state.value
        val audible = snapshot.isPlaying && s.book != null && !s.isLoading && s.error == null
        if (audible != reportedPlaying) {
            reportedPlaying = audible
            when {
                audible && rebuildQuiet -> rebuildQuiet = false
                audible -> { quietStop = false; dispatcher.play(s) }
                rebuildQuiet || quietStop -> quietStop = false
                else -> dispatcher.pause(s)
            }
        }
        if (audible) startTicking() else stopTicking()
    }

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (true) {
                delay(TICK_MILLIS)
                _state.update { it.withPosition(engine.snapshot().positionSeconds) }
                pushNowPlaying()
                if (artworkFailed && artworkRetry.fetchIsDue(timeSource.nowMillis())) loadArtwork()
                dispatcher.tick(_state.value)
            }
        }
    }
    private fun stopTicking() { tickJob?.cancel(); tickJob = null }

    // ---- artwork and now playing ----
    private fun loadArtwork() {
        val uuid = _state.value.book?.bookUuid ?: return
        artworkRetry.noteCurrent(uuid)
        if (artworkJob?.isActive == true || !artworkRetry.fetchIsDue(timeSource.nowMillis())) return
        val gen = generation
        artworkJob = scope.launch {
            val result = artworkSource.artwork(uuid)
            if (gen != generation) return@launch
            when (result) {
                is CoverArtwork.Found -> { image = NowPlayingImage.Cover(result.bytes); artworkFailed = false }
                CoverArtwork.NoCover -> { image = NowPlayingImage.Plate; artworkFailed = false }
                CoverArtwork.Failed -> {
                    if (image == NowPlayingImage.None) image = NowPlayingImage.Plate
                    artworkFailed = true
                    artworkRetry.noteFailure(timeSource.nowMillis())
                }
            }
            pushNowPlaying()
        }
    }
    private fun pushNowPlaying() {
        val s = _state.value
        val book = s.book ?: return
        val next = NowPlaying(book.bookUuid, book.title, book.author, s.chapters, s.durationSeconds, s.currentChapterIndex, image)
        if (next == lastNowPlaying) return
        lastNowPlaying = next
        withEngine { engine.showNowPlaying(next) }
    }

    // ---- engine connection: calls before connect queue up and run in call order ----
    private fun withEngine(block: () -> Unit) {
        if (engineReady) { runEngine(block); return }
        queuedEngineCalls.addLast(block)
        ensureConnecting()
    }
    private fun runEngine(block: () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e } catch (t: Throwable) { Log.w(TAG, "A player command failed", t) }
    }
    private fun ensureConnecting() {
        if (connectJob?.isActive == true) return
        connectJob = scope.launch {
            try { engine.awaitReady() } catch (e: CancellationException) { throw e } catch (t: Throwable) {
                Log.w(TAG, "Couldn't connect to the playback service", t); queuedEngineCalls.clear(); return@launch
            }
            engineReady = true
            while (queuedEngineCalls.isNotEmpty()) runEngine(queuedEngineCalls.removeFirst())
        }
    }
    private suspend fun awaitEngine() {
        if (!engineReady) { ensureConnecting(); connectJob?.join() }
        check(engineReady) { "The playback service is unavailable" }
    }

    private suspend fun readBook(uuid: String): Book? = try {
        (libraryRepository.getBookFlow(uuid).first() as? BookResult.Success)?.book
    } catch (e: CancellationException) { throw e } catch (t: Throwable) { Log.w(TAG, "Couldn't read the book", t); null }

    private fun PlaybackState.withPosition(seconds: Double) =
        copy(positionSeconds = seconds, currentChapterIndex = chapterIndexAt(chapters, seconds))

    internal companion object {
        const val TAG = "AudiobookController"
        const val TICK_MILLIS = 500L
        /** Mirrors Book's private AUDIO_FORMATS. */
        val AUDIO_FORMATS = setOf("m4b", "m4a", "mp3", "aac", "flac", "ogg", "opus", "wav")
        fun Book.firstAudioFileId(): Long? =
            bookFiles.filter { it.format.lowercase() in AUDIO_FORMATS }.minByOrNull { it.ordinal }?.id
        fun Book.authorOrNull(): String? = authorDisplay.takeIf { creators.any { it.name.isNotBlank() } }
    }
}

internal fun AudiobookManifestError.toPlaybackError(): PlaybackError = when (this) {
    AudiobookManifestError.NoAudioParts -> PlaybackError.NoAudioParts
    AudiobookManifestError.InvalidManifest -> PlaybackError.TimelineFailed
    is AudiobookManifestError.Http -> PlaybackError.Server(serverMessage)
    else -> PlaybackError.Server(null)
}
