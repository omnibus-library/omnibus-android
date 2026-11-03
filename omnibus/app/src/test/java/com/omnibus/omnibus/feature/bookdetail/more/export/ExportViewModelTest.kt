package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.data.auth.CurrentUser
import com.omnibus.omnibus.data.auth.FakeAuthRepository
import com.omnibus.omnibus.data.export.ExportSaveResult
import com.omnibus.omnibus.data.export.ExportedFile
import com.omnibus.omnibus.data.export.KindleSendResult
import com.omnibus.omnibus.data.library.BookFile
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.data.network.FakeConnectivityMonitor
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File

private const val BOOK_UUID = "uuid-1"

private val reader = CurrentUser(id = 1, canEdit = false, canUpload = false, hiddenFormats = emptySet(), canDownload = true)
private val otherReader = CurrentUser(id = 2, canEdit = false, canUpload = false, hiddenFormats = emptySet(), canDownload = false)

/** A book with an EPUB and an m4b, so every row is available. */
private val dune = testBook(
    1,
    title = "Dune",
    formats = listOf("epub", "m4b"),
    bookFiles = listOf(
        BookFile(10, "epub", "dune.epub", 1, null, 412_000, null),
        BookFile(11, "m4b", "dune.m4b", 1, null, 138_000_000, null),
    ),
)

/** A gate that answers the call at once. */
private fun kindleAnswer(result: KindleSendResult): CompletableDeferred<KindleSendResult> = CompletableDeferred(result)

/** A gate the test completes itself, so the call stays in flight until then. */
private fun pendingKindle(): CompletableDeferred<KindleSendResult> = CompletableDeferred()

private fun saveAnswer(result: ExportSaveResult): CompletableDeferred<ExportSaveResult> = CompletableDeferred(result)

private fun pendingSave(): CompletableDeferred<ExportSaveResult> = CompletableDeferred()

private fun savedFile(name: String): ExportSaveResult =
    ExportSaveResult.Saved(listOf(ExportedFile(File(name), "application/epub+zip")))

/** The fakes behind one ViewModel. */
private class Harness(online: Boolean = true) {
    val repo = FakeExportRepository()
    val auth = FakeAuthRepository()
    val monitor = FakeConnectivityMonitor(online)
    val vm = ExportViewModel(BOOK_UUID, repo, auth, monitor)
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    describe("state from authentication and connectivity") {

        it("#118 TC-15: starts closed and idle, then follows the user and connectivity") {
            val h = Harness(online = true)
            with(h.vm.uiState.value) {
                sheetOpen shouldBe false
                kindle shouldBe KindlePhase.Idle
                saving.shouldBeNull()
                saveError.shouldBeNull()
                share.shouldBeNull()
                user.shouldBeNull()
            }

            h.auth.currentUserState.value = reader
            h.vm.uiState.value.user shouldBe reader
            h.auth.currentUserState.value = otherReader
            h.vm.uiState.value.user shouldBe otherReader

            h.monitor.isOnline.value = false
            h.vm.uiState.value.isOnline shouldBe false
            h.monitor.isOnline.value = true
            h.vm.uiState.value.isOnline shouldBe true
        }
    }

    describe("opening and closing the sheet") {

        it("#118 TC-16: openSheet opens it, resets a finished send and the last error, and keeps a send in flight") {
            val fresh = Harness()
            fresh.vm.openSheet()
            fresh.vm.uiState.value.sheetOpen shouldBe true
            fresh.vm.uiState.value.kindle shouldBe KindlePhase.Idle

            val sent = Harness()
            sent.vm.sendToKindle()
            sent.vm.uiState.value.kindle shouldBe KindlePhase.Sent
            sent.vm.openSheet()
            sent.vm.uiState.value.kindle shouldBe KindlePhase.Idle

            val failed = Harness()
            failed.repo.kindleGates.addLast(kindleAnswer(KindleSendResult.Unreachable))
            failed.vm.sendToKindle()
            failed.vm.uiState.value.kindle shouldBe KindlePhase.Failed(KindleFailure.Unreachable)
            failed.vm.openSheet()
            failed.vm.uiState.value.kindle shouldBe KindlePhase.Idle

            val sending = Harness()
            val gate = pendingKindle()
            sending.repo.kindleGates.addLast(gate)
            sending.vm.sendToKindle()
            sending.vm.openSheet()
            sending.vm.uiState.value.kindle shouldBe KindlePhase.Sending
            gate.complete(KindleSendResult.Sent)

            val withError = Harness()
            withError.repo.saveGates.addLast(saveAnswer(ExportSaveResult.Forbidden))
            withError.vm.save(ExportSave.Kobo, dune)
            withError.vm.uiState.value.saveError shouldBe ExportSaveError.Forbidden
            withError.vm.openSheet()
            withError.vm.uiState.value.saveError.shouldBeNull()
        }

        it("#118 TC-17: closeSheet closes an idle sheet and is ignored while a send is in flight") {
            val idle = Harness()
            idle.vm.openSheet()
            idle.vm.closeSheet()
            idle.vm.uiState.value.sheetOpen shouldBe false

            val sending = Harness()
            val gate = pendingKindle()
            sending.repo.kindleGates.addLast(gate)
            sending.vm.openSheet()
            sending.vm.sendToKindle()
            sending.vm.closeSheet()
            sending.vm.uiState.value.sheetOpen shouldBe true
            gate.complete(KindleSendResult.Sent)
        }
    }

    describe("sending to Kindle") {

        it("#118 TC-18: sendToKindle shows Sending at once, calls the repository once, then shows Sent") {
            val h = Harness()
            val gate = pendingKindle()
            h.repo.kindleGates.addLast(gate)

            h.vm.sendToKindle()
            h.vm.uiState.value.kindle shouldBe KindlePhase.Sending
            h.repo.kindleCalls shouldBe listOf("uuid-1")

            gate.complete(KindleSendResult.Sent)
            h.vm.uiState.value.kindle shouldBe KindlePhase.Sent
            h.repo.kindleCalls shouldBe listOf("uuid-1")
        }

        it("#118 TC-19: each send outcome maps to the matching failure, keeping the server's message") {
            val cases: List<Pair<KindleSendResult, KindlePhase>> = listOf(
                KindleSendResult.Rejected("mailbox full") to KindlePhase.Failed(KindleFailure.Undelivered, "mailbox full"),
                KindleSendResult.Rejected(null) to KindlePhase.Failed(KindleFailure.Undelivered, null),
                KindleSendResult.Unconfirmed to KindlePhase.Failed(KindleFailure.Unconfirmed),
                KindleSendResult.NoAddress to KindlePhase.Failed(KindleFailure.NoAddress),
                KindleSendResult.NotConfigured to KindlePhase.Failed(KindleFailure.NotConfigured),
                KindleSendResult.NotFound to KindlePhase.Failed(KindleFailure.NotFound),
                KindleSendResult.Unreachable to KindlePhase.Failed(KindleFailure.Unreachable),
                KindleSendResult.Failed to KindlePhase.Failed(KindleFailure.Unknown),
                KindleSendResult.NoServer to KindlePhase.Failed(KindleFailure.Unknown),
            )
            cases.forEach { (result, expected) ->
                withClue("result=$result") {
                    val h = Harness()
                    h.repo.kindleGates.addLast(kindleAnswer(result))
                    h.vm.sendToKindle()
                    h.vm.uiState.value.kindle shouldBe expected
                }
            }
        }

        it("#118 TC-20: sendToKindle is ignored while sending, after Sent and offline; after a failure it sends only when called") {
            val sending = Harness()
            val gate = pendingKindle()
            sending.repo.kindleGates.addLast(gate)
            sending.vm.sendToKindle()
            sending.vm.sendToKindle()
            sending.repo.kindleCalls.size shouldBe 1
            gate.complete(KindleSendResult.Sent)

            sending.vm.sendToKindle()
            sending.repo.kindleCalls.size shouldBe 1

            val offline = Harness(online = false)
            offline.vm.sendToKindle()
            offline.repo.kindleCalls.size shouldBe 0

            val failed = Harness()
            failed.repo.kindleGates.addLast(kindleAnswer(KindleSendResult.Unreachable))
            failed.vm.sendToKindle()
            failed.vm.uiState.value.kindle shouldBe KindlePhase.Failed(KindleFailure.Unreachable)
            failed.repo.kindleCalls.size shouldBe 1

            failed.vm.sendToKindle()
            failed.repo.kindleCalls.size shouldBe 2
            failed.vm.uiState.value.kindle shouldBe KindlePhase.Sent
        }

        it("#118 TC-21: kindleSettingsTapped closes the open sheet") {
            val h = Harness()
            h.vm.openSheet()
            h.vm.kindleSettingsTapped()
            h.vm.uiState.value.sheetOpen shouldBe false
        }
    }

    describe("saving a copy") {

        it("#118 TC-22: each save calls the repository with the book's title and target, and is saving while in flight") {
            val h = Harness()
            val cases = listOf(
                Triple(ExportSave.Kobo, "kepub:uuid-1:Dune", ExportSaveResult.Saved(emptyList())),
                Triple(ExportSave.Ebook, "ebook:uuid-1:Dune:epub:412000", ExportSaveResult.Saved(emptyList())),
                Triple(ExportSave.Audio, "audio:uuid-1:Dune:m4b:138000000", ExportSaveResult.Saved(emptyList())),
            )
            for ((kind, call, result) in cases) {
                withClue("kind=$kind") {
                    val gate = pendingSave()
                    h.repo.saveGates.addLast(gate)
                    h.vm.save(kind, dune)
                    h.vm.uiState.value.saving shouldBe kind
                    h.repo.saveCalls.last() shouldBe call
                    gate.complete(result)
                    h.vm.uiState.value.saving.shouldBeNull()
                }
            }
        }

        it("#118 TC-23: one save at a time, offline saves ignored, and the Kindle send does not block a save") {
            val h = Harness()
            val kobo = pendingSave()
            h.repo.saveGates.addLast(kobo)
            h.vm.save(ExportSave.Kobo, dune)
            h.vm.save(ExportSave.Ebook, dune)
            h.repo.saveCalls.size shouldBe 1
            h.vm.uiState.value.saving shouldBe ExportSave.Kobo
            kobo.complete(ExportSaveResult.Saved(emptyList()))

            h.vm.save(ExportSave.Audio, dune)
            h.repo.saveCalls.size shouldBe 2

            val kindle = pendingKindle()
            h.repo.kindleGates.addLast(kindle)
            h.vm.sendToKindle()
            val ebook = pendingSave()
            h.repo.saveGates.addLast(ebook)
            h.vm.save(ExportSave.Ebook, dune)
            h.repo.saveCalls.size shouldBe 3
            h.vm.uiState.value.saving shouldBe ExportSave.Ebook
            h.vm.uiState.value.kindle shouldBe KindlePhase.Sending
            ebook.complete(ExportSaveResult.Saved(emptyList()))
            kindle.complete(KindleSendResult.Sent)

            val offline = Harness(online = false)
            offline.vm.save(ExportSave.Kobo, dune)
            offline.repo.saveCalls.size shouldBe 0
            offline.vm.uiState.value.saving.shouldBeNull()
        }

        it("#118 TC-24: a finished save requests a share in order; a stale share id is ignored and the current one clears it") {
            val h = Harness()
            h.repo.saveGates.addLast(saveAnswer(savedFile("Dune.kepub.epub")))
            h.vm.save(ExportSave.Kobo, dune)
            val first = h.vm.uiState.value.share
            first?.files shouldBe listOf(ExportedFile(File("Dune.kepub.epub"), "application/epub+zip"))
            h.vm.uiState.value.saving.shouldBeNull()

            val parts = listOf(
                ExportedFile(File("Dune - part 01.m4b"), "audio/mp4"),
                ExportedFile(File("Dune - part 02.m4b"), "audio/mp4"),
                ExportedFile(File("Dune - part 03.m4b"), "audio/mp4"),
            )
            h.repo.saveGates.addLast(saveAnswer(ExportSaveResult.Saved(parts)))
            h.vm.save(ExportSave.Audio, dune)
            val second = h.vm.uiState.value.share
            second?.files shouldBe parts
            (second!!.eventId > first!!.eventId) shouldBe true
            h.vm.uiState.value.saving.shouldBeNull()

            h.vm.shareShown(first.eventId)
            h.vm.uiState.value.share shouldBe second
            h.vm.shareShown(second.eventId)
            h.vm.uiState.value.share.shouldBeNull()
        }

        it("#118 TC-25: each failed save sets its error, a new save clears it, and shareFailed sets Failed") {
            val cases = listOf(
                ExportSaveResult.Forbidden to ExportSaveError.Forbidden,
                ExportSaveResult.NotFound to ExportSaveError.NotFound,
                ExportSaveResult.Unreachable to ExportSaveError.Unreachable,
                ExportSaveResult.NoSpace to ExportSaveError.NoSpace,
                ExportSaveResult.Failed to ExportSaveError.Failed,
                ExportSaveResult.NoServer to ExportSaveError.Failed,
            )
            cases.forEach { (result, expected) ->
                withClue("result=$result") {
                    val h = Harness()
                    h.repo.saveGates.addLast(saveAnswer(result))
                    h.vm.save(ExportSave.Kobo, dune)
                    h.vm.uiState.value.saveError shouldBe expected
                    h.vm.uiState.value.share.shouldBeNull()
                    h.vm.uiState.value.saving.shouldBeNull()
                }
            }

            val h = Harness()
            h.repo.saveGates.addLast(saveAnswer(ExportSaveResult.Forbidden))
            h.vm.save(ExportSave.Kobo, dune)
            h.vm.uiState.value.saveError shouldBe ExportSaveError.Forbidden
            val pending = CompletableDeferred<ExportSaveResult>()
            h.repo.saveGates.addLast(pending)
            h.vm.save(ExportSave.Kobo, dune)
            h.vm.uiState.value.saveError.shouldBeNull()
            pending.complete(savedFile("Dune.kepub.epub"))

            h.vm.shareFailed()
            h.vm.uiState.value.saveError shouldBe ExportSaveError.Failed
        }

        it("#118 TC-26: closing the sheet cancels a save in flight, which then sets no share and no error") {
            val h = Harness()
            val gate = pendingSave()
            h.repo.saveGates.addLast(gate)
            h.vm.openSheet()
            h.vm.save(ExportSave.Kobo, dune)

            h.vm.closeSheet()
            h.repo.saveCancelled shouldBe true
            gate.complete(savedFile("Dune.kepub.epub"))
            h.vm.uiState.value.saving.shouldBeNull()
            h.vm.uiState.value.share.shouldBeNull()
            h.vm.uiState.value.saveError.shouldBeNull()

            h.vm.openSheet()
            h.repo.saveGates.addLast(saveAnswer(ExportSaveResult.Saved(emptyList())))
            h.vm.save(ExportSave.Kobo, dune)
            h.repo.saveCalls.size shouldBe 2
            h.vm.uiState.value.share?.files shouldBe emptyList()
        }

        it("#118 TC-27: going offline does not cancel a save; its result still requests a share") {
            val h = Harness()
            val gate = pendingSave()
            h.repo.saveGates.addLast(gate)
            h.vm.save(ExportSave.Kobo, dune)

            h.monitor.isOnline.value = false
            h.vm.uiState.value.isOnline shouldBe false
            h.repo.saveCancelled shouldBe false

            gate.complete(savedFile("Dune.kepub.epub"))
            h.vm.uiState.value.share?.files shouldBe listOf(ExportedFile(File("Dune.kepub.epub"), "application/epub+zip"))
        }
    }
})
