package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.data.audiobook.AudiobookManifest
import com.omnibus.omnibus.data.audiobook.AudiobookManifestResult
import com.omnibus.omnibus.data.audiobook.AudiobookRepository
import com.omnibus.omnibus.data.audiobook.TestAudiobookRepository
import com.omnibus.omnibus.data.audiobook.remote.TestRecentListeningSource
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.BookResult
import com.omnibus.omnibus.data.library.Creator
import com.omnibus.omnibus.data.library.TestBookAvailability
import com.omnibus.omnibus.data.library.TestLibraryRepository
import com.omnibus.omnibus.data.library.LibraryRepository
import com.omnibus.omnibus.data.library.ProgressFormat
import com.omnibus.omnibus.data.library.ProgressRecord
import com.omnibus.omnibus.data.library.ResumePoint
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.randomBoolean
import io.kotest.matchers.shouldBe
import java.util.Optional
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import io.kotest.assertions.withClue

/**
 * A [DefaultAudiobookController] built the way Hilt builds it, with every fake behind it, on one virtual clock. The
 * `DefaultAudiobookController*Test` specs share it.
 *
 * It starts with nothing staged: [stageBook] makes "book-1" answer from the library and the repository, and
 * [loadAndSettle] does that first. The book is [multiPartManifest]: 3000 s long, with chapters starting at 0, 700 and
 * 1900 s. Pass [order] to log the engine commands, the reads and the resume calls into one list, in the order they
 * happen. Call [close] when the spec's test is done.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerRig(
    val scheduler: TestCoroutineScheduler,
    resolver: ResumeStartResolver? = null,
    rateStore: PlaybackRateStore? = null,
    sleepTimer: SleepTimer? = null,
    listeners: Set<PlaybackListener> = emptySet(),
    online: Boolean = randomBoolean(),
    order: MutableList<String>? = null,
) {
    val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    val engine = TestAudioEngine()
    val repository = TestAudiobookRepository()
    val library = TestLibraryRepository()
    val recent = TestRecentListeningSource()
    val availability = TestBookAvailability()
    val artwork = TestCoverArtworkSource()
    val connectivity = TestConnectivityMonitor(online)
    val background = TestAppBackgroundSignal()

    private val wiredEngine: AudioEngine = order?.let { LoggingEngine(engine, it) } ?: engine
    private val reads: AudiobookRepository = order?.let { repository.logging(it) } ?: repository
    private val books: LibraryRepository = order?.let { library.logging(it) } ?: library

    val controller = DefaultAudiobookController(
        engine = wiredEngine,
        repository = reads,
        libraryRepository = books,
        recentListening = recent,
        bookAvailability = availability,
        artworkSource = artwork,
        connectivity = connectivity,
        appBackground = background,
        resumeStartResolver = Optional.ofNullable(resolver),
        playbackRateStore = Optional.ofNullable(rateStore),
        sleepTimer = Optional.ofNullable(sleepTimer),
        listeners = listeners,
        timeSource = TimeSource { scheduler.currentTime },
        scope = scope,
    )

    init {
        // Starts the controller's collectors, so they see what a test emits next.
        scheduler.runCurrent()
    }

    /** Makes "book-1" answer from the library and the repository, keeping any answer the test has already set. */
    fun stageBook() {
        library.bookResult = library.bookResult ?: BookResult.Success(
            randomBook().copy(
                id = 1,
                title = "The Book",
                creators = listOf(Creator("An Author", null, null, null)),
                formats = listOf("m4b"),
                uniqueIdentifier = "book-1",
                bookFiles = emptyList(),
            ),
        )
        repository.result = repository.result ?: AudiobookManifestResult.Success(multiPartManifest())
    }

    /** Runs whatever is due now, without moving the clock. */
    fun settle() = scheduler.runCurrent()

    /** Moves the clock forward by [millis], running everything that falls due on the way. */
    fun advance(millis: Long) {
        scheduler.advanceTimeBy(millis)
        scheduler.runCurrent()
    }

    /** Stages the book, starts a load and lets it run until it waits on something the test controls. */
    fun loadAndSettle(
        bookUuid: String = "book-1",
        bookFileId: Long? = null,
        positionSeconds: Double? = null,
        autoplay: Boolean = true,
    ) {
        stageBook()
        controller.load(bookUuid, bookFileId, positionSeconds, autoplay)
        settle()
    }

    /** Cancels the controller's scope, which ends its collectors and any load or tick still running. */
    fun close() {
        scope.cancel()
    }
}

/**
 * A [ResumeStartResolver] whose pendings answer what the test scripts: the saved file id and the settle answer, which can
 * be held by a deferred. Each [begin] is recorded, and each pending is kept so a test can read its calls.
 */
class ScriptedResolver(
    private val savedFileId: Long? = null,
    private val settleAnswer: CompletableDeferred<Double?> = answering(null),
) : ResumeStartResolver {

    /** The `(bookUuid, explicitFileId, explicitPositionSeconds)` of each [begin], in call order. */
    val begins = mutableListOf<Triple<String, Long?, Double?>>()

    /** The pending each [begin] returned, in call order. */
    val pendings = mutableListOf<TestPendingResume>()

    override fun begin(bookUuid: String, explicitFileId: Long?, explicitPositionSeconds: Double?): PendingResume {
        begins += Triple(bookUuid, explicitFileId, explicitPositionSeconds)
        return TestPendingResume().also {
            it.savedFileId = savedFileId
            it.settleAnswer = settleAnswer
            pendings += it
        }
    }
}

/** A deferred that is already complete with [value], for a settle or rate answer that needs no waiting. */
fun answering(value: Double?): CompletableDeferred<Double?> = CompletableDeferred<Double?>().apply { complete(value) }

/**
 * A listener that records each callback by name, in call order. A non-null [unloadGate] holds `onUnload` until it
 * completes. [count] counts the calls whose name starts with a prefix.
 */
class CallRecorder(var unloadGate: CompletableDeferred<Unit>? = null) : PlaybackListener {

    /** Every callback, in call order: `settled:<start>`, `tick`, `play`, `pause`, `background`, `ended`, `unload:<reason>`. */
    val calls = mutableListOf<String>()

    /** The state each tick was given. */
    val ticks = mutableListOf<PlaybackState>()

    /** The state each `onEnded` was given. */
    val ended = mutableListOf<PlaybackState>()

    /** The state each `onAppBackground` was given. */
    val backgrounds = mutableListOf<PlaybackState>()

    override fun onSettled(state: PlaybackState, startSeconds: Double) {
        calls += "settled:$startSeconds"
    }

    override fun onTick(state: PlaybackState) {
        calls += "tick"
        ticks += state
    }

    override fun onPlay(state: PlaybackState) {
        calls += "play"
    }

    override fun onPause(state: PlaybackState) {
        calls += "pause"
    }

    override fun onAppBackground(state: PlaybackState) {
        calls += "background"
        backgrounds += state
    }

    override fun onEnded(state: PlaybackState) {
        calls += "ended"
        ended += state
    }

    override suspend fun onUnload(state: PlaybackState, reason: UnloadReason) {
        calls += "unload:$reason"
        unloadGate?.await()
        calls += "unloaded:$reason"
    }

    /** How many calls have a name starting with [prefix]. */
    fun count(prefix: String): Int = calls.count { it.startsWith(prefix) }
}

/** Asserts that every step appears in this list, and that they appear in the order given. */
fun List<String>.shouldBeInOrder(vararg steps: String) {
    val positions = steps.map { indexOf(it) }
    withClue("expected $steps in order, got $this") {
        positions.all { it >= 0 } shouldBe true
        positions.zipWithNext().all { (before, after) -> before < after } shouldBe true
    }
}

/** A search-result-free resume point for [bookUuid], listened to at [position] in file [bookFileId]. */
fun resumePoint(
    bookUuid: String,
    bookFileId: Long?,
    position: Double?,
): ResumePoint = ResumePoint(
    record = ProgressRecord(
        bookUuid = bookUuid,
        format = ProgressFormat.Listening,
        epubCfi = null,
        audioPositionSeconds = position,
        progressPercent = null,
        updatedAt = 0L,
        clientUpdatedAt = null,
        bookFileId = bookFileId,
        totalDurationSeconds = null,
        resolved = null,
    ),
    book = randomBook().copy(id = 1, uniqueIdentifier = bookUuid),
    audioPart = null,
    audioPartCount = null,
    playbackRate = null,
)

/** An audio file of a book, with the given id, format and ordinal. */
fun bookFile(id: Long, format: String, ordinal: Int): BookFile =
    BookFile(id = id, format = format, filename = "file-$id.$format", ordinal = ordinal, label = null, sizeBytes = 0L, etag = null)

/** The three-part `direct` manifest of [multiPartManifest], with the first part's length replaced by [seconds]. */
fun manifestWithFirstPart(seconds: Double): AudiobookManifest.Direct {
    val manifest = multiPartManifest()
    return manifest.copy(parts = manifest.parts.mapIndexed { index, part -> if (index == 0) part.copy(durationSeconds = seconds) else part })
}

/** An `hls` manifest for book "book-1", with the file id 11 and the same headers as [multiPartManifest]. */
fun hlsManifest(): AudiobookManifest.Hls = AudiobookManifest.Hls(
    bookFileId = 11L,
    playlistUrl = "https://server.test/api/audiobooks/book-1/playlist.m3u8",
    requestHeaders = mapOf("Authorization" to "Bearer secret", "Accept" to "*/*"),
)

/** Records every engine command into [order], then forwards it to [inner]. */
private class LoggingEngine(private val inner: TestAudioEngine, private val order: MutableList<String>) :
    AudioEngine by inner {

    override suspend fun awaitReady() {
        order += "awaitReady"
        inner.awaitReady()
    }

    override suspend fun setSource(source: AudioSource) {
        order += "setSource"
        inner.setSource(source)
    }

    override fun play() {
        order += "play"
        inner.play()
    }

    override fun pause() {
        order += "pause"
        inner.pause()
    }

    override fun seekTo(bookSeconds: Double) {
        order += "seekTo($bookSeconds)"
        inner.seekTo(bookSeconds)
    }

    override fun setSpeed(speed: Double) {
        order += "setSpeed($speed)"
        inner.setSpeed(speed)
    }
}

private fun AudiobookRepository.logging(order: MutableList<String>): AudiobookRepository {
    val inner = this
    return object : AudiobookRepository by inner {
        override suspend fun getManifest(bookUuid: String, bookFileId: Long?): AudiobookManifestResult {
            order += "getManifest"
            return inner.getManifest(bookUuid, bookFileId)
        }
    }
}

private fun LibraryRepository.logging(order: MutableList<String>): LibraryRepository {
    val inner = this
    return object : LibraryRepository by inner {
        override fun getBookFlow(uuid: String): Flow<BookResult> = flow {
            order += "getBook"
            emitAll(inner.getBookFlow(uuid))
        }
    }
}
