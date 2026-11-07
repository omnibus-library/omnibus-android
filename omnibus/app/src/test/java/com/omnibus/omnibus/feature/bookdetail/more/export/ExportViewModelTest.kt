package com.omnibus.omnibus.feature.bookdetail.more.export

import com.omnibus.omnibus.data.auth.TestAuthRepository
import com.omnibus.omnibus.data.auth.randomCurrentUser
import com.omnibus.omnibus.data.export.ExportSaveResult
import com.omnibus.omnibus.data.export.ExportedFile
import com.omnibus.omnibus.data.export.KindleSendResult
import com.omnibus.omnibus.data.library.Book
import com.omnibus.omnibus.data.library.randomBook
import com.omnibus.omnibus.data.library.randomBookFile
import com.omnibus.omnibus.data.network.TestConnectivityMonitor
import com.omnibus.omnibus.testing.randomEnum
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import java.io.File

/** A book with an EPUB and an m4b, so every save has a file. */
private fun bookWithEveryFile(): Book = randomBook().copy(
    formats = listOf("epub", "m4b"),
    bookFiles = listOf(randomBookFile().copy(format = "epub"), randomBookFile().copy(format = "m4b")),
)

private fun savedFiles(): List<ExportedFile> = listOf(ExportedFile(File(randomString()), randomString()))

/** A repository whose next send is held until the test completes the returned gate. */
private fun TestExportRepository.holdKindle(): CompletableDeferred<KindleSendResult> =
    CompletableDeferred<KindleSendResult>().also { kindleGates.addLast(it) }

/** A repository whose next save is held until the test completes the returned gate. */
private fun TestExportRepository.holdSave(): CompletableDeferred<ExportSaveResult> =
    CompletableDeferred<ExportSaveResult>().also { saveGates.addLast(it) }

private fun TestExportRepository.answerKindle(result: KindleSendResult) {
    kindleGates.addLast(CompletableDeferred(result))
}

private fun TestExportRepository.answerSave(result: ExportSaveResult) {
    saveGates.addLast(CompletableDeferred(result))
}

private fun online() = TestConnectivityMonitor(online = true)

@OptIn(ExperimentalCoroutinesApi::class)
class ExportViewModelTest : DescribeSpec({

    beforeSpec { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    afterSpec { Dispatchers.resetMain() }

    fun sut(
        bookUuid: String = randomString(),
        repo: TestExportRepository = TestExportRepository(),
        auth: TestAuthRepository = TestAuthRepository(),
        monitor: TestConnectivityMonitor = TestConnectivityMonitor(),
    ) = ExportViewModel(bookUuid, repo, auth, monitor)

    describe("the signed-in user") {
        it("#118 TC-15: follows the auth repository") {
            val user = randomCurrentUser()
            val auth = TestAuthRepository()
            val vm = sut(auth = auth)

            auth.currentUserState.value = user

            vm.uiState.value.user shouldBe user
        }
    }

    describe("opening the sheet") {
        context("when a send has finished") {
            it("#118 TC-16: starts the Kindle row afresh, for a sent or failed send") {
                checkAll(Exhaustive.of(KindleSendResult.Sent, KindleSendResult.Unreachable)) { result ->
                    val repo = TestExportRepository().apply { answerKindle(result) }
                    val vm = sut(repo = repo, monitor = online())
                    vm.sendToKindle()

                    vm.openSheet()

                    vm.uiState.value.kindle shouldBe KindlePhase.Idle
                }
            }
        }

        context("when a send is in flight") {
            it("#118 TC-16: keeps showing it") {
                val repo = TestExportRepository().apply { holdKindle() }
                val vm = sut(repo = repo, monitor = online())
                vm.sendToKindle()

                vm.openSheet()

                vm.uiState.value.kindle shouldBe KindlePhase.Sending
            }
        }

        context("when the last save failed") {
            it("#118 TC-16: clears the error") {
                val repo = TestExportRepository().apply { answerSave(ExportSaveResult.Forbidden) }
                val vm = sut(repo = repo, monitor = online())
                vm.save(ExportSave.Kobo, bookWithEveryFile())

                vm.openSheet()

                vm.uiState.value.saveError.shouldBeNull()
            }
        }
    }

    describe("closing the sheet") {
        context("when a send is in flight") {
            it("#118 TC-17: stays open") {
                val repo = TestExportRepository().apply { holdKindle() }
                val vm = sut(repo = repo, monitor = online())
                vm.openSheet()
                vm.sendToKindle()

                vm.closeSheet()

                vm.uiState.value.sheetOpen shouldBe true
            }
        }

        context("when the user taps through to Kindle settings") {
            it("#118 TC-17, TC-21: closes") {
                val vm = sut(monitor = online())
                vm.openSheet()

                vm.kindleSettingsTapped()

                vm.uiState.value.sheetOpen shouldBe false
            }
        }

        context("when a save is in flight") {
            it("#118 TC-26: cancels it, so it requests no share") {
                val repo = TestExportRepository()
                val gate = repo.holdSave()
                val vm = sut(repo = repo, monitor = online())
                vm.openSheet()
                vm.save(randomEnum(), bookWithEveryFile())

                vm.closeSheet()
                gate.complete(ExportSaveResult.Saved(savedFiles()))

                vm.uiState.value.saving.shouldBeNull()
                vm.uiState.value.share.shouldBeNull()
            }
        }
    }

    describe("sending to Kindle") {
        it("#118 TC-18: shows sending for this book until the server answers") {
            val uuid = randomString()
            val repo = TestExportRepository()
            val gate = repo.holdKindle()
            val vm = sut(uuid, repo, monitor = online())

            vm.sendToKindle()
            vm.uiState.value.kindle shouldBe KindlePhase.Sending
            repo.kindleCalls shouldBe listOf(uuid)

            gate.complete(KindleSendResult.Sent)
            vm.uiState.value.kindle shouldBe KindlePhase.Sent
        }

        it("#118 TC-19: maps each failed outcome to its reason, keeping the server's message") {
            checkAll(
                Exhaustive.of(
                    KindleSendResult.Rejected("mailbox full") to KindlePhase.Failed(KindleFailure.Undelivered, "mailbox full"),
                    KindleSendResult.Rejected(null) to KindlePhase.Failed(KindleFailure.Undelivered, null),
                    KindleSendResult.Unconfirmed to KindlePhase.Failed(KindleFailure.Unconfirmed),
                    KindleSendResult.NoAddress to KindlePhase.Failed(KindleFailure.NoAddress),
                    KindleSendResult.NotConfigured to KindlePhase.Failed(KindleFailure.NotConfigured),
                    KindleSendResult.NotFound to KindlePhase.Failed(KindleFailure.NotFound),
                    KindleSendResult.Unreachable to KindlePhase.Failed(KindleFailure.Unreachable),
                    KindleSendResult.Failed to KindlePhase.Failed(KindleFailure.Unknown),
                    KindleSendResult.NoServer to KindlePhase.Failed(KindleFailure.Unknown),
                ),
            ) { (result, expected) ->
                val repo = TestExportRepository().apply { answerKindle(result) }
                val vm = sut(repo = repo, monitor = online())

                vm.sendToKindle()

                vm.uiState.value.kindle shouldBe expected
            }
        }

        context("when a send has started") {
            it("#118 TC-20: ignores another send") {
                val repo = TestExportRepository()
                val gate = repo.holdKindle()
                val vm = sut(repo = repo, monitor = online())

                vm.sendToKindle()
                vm.sendToKindle()
                repo.kindleCalls.size shouldBe 1

                gate.complete(KindleSendResult.Sent)
                vm.sendToKindle()
                repo.kindleCalls.size shouldBe 1
            }
        }

        context("when the last send failed") {
            it("#118 TC-20: sends again") {
                val repo = TestExportRepository().apply {
                    answerKindle(KindleSendResult.Unreachable)
                    answerKindle(KindleSendResult.Sent)
                }
                val vm = sut(repo = repo, monitor = online())

                vm.sendToKindle()
                vm.sendToKindle()

                vm.uiState.value.kindle shouldBe KindlePhase.Sent
            }
        }
    }

    describe("saving a copy") {
        it("#118 TC-22: downloads the book's file for each kind, showing it as saving") {
            val uuid = randomString()
            val book = bookWithEveryFile()
            val title = book.displayTitle
            val epubBytes = book.bookFiles[0].sizeBytes
            val audioBytes = book.bookFiles[1].sizeBytes

            checkAll(
                Exhaustive.of(
                    ExportSave.Kobo to "kepub:$uuid:$title",
                    ExportSave.Ebook to "ebook:$uuid:$title:epub:$epubBytes",
                    ExportSave.Audio to "audio:$uuid:$title:m4b:$audioBytes",
                ),
            ) { (kind, call) ->
                val repo = TestExportRepository().apply { holdSave() }
                val vm = sut(uuid, repo, monitor = online())

                vm.save(kind, book)

                vm.uiState.value.saving shouldBe kind
                repo.saveCalls shouldBe listOf(call)
            }
        }

        context("when a save is in flight") {
            it("#118 TC-23: ignores another save") {
                val repo = TestExportRepository().apply { holdSave() }
                val vm = sut(repo = repo, monitor = online())

                vm.save(ExportSave.Kobo, bookWithEveryFile())
                vm.save(ExportSave.Ebook, bookWithEveryFile())

                repo.saveCalls.size shouldBe 1
                vm.uiState.value.saving shouldBe ExportSave.Kobo
            }
        }

        context("when the save finishes") {
            it("#118 TC-24: requests a share of its files") {
                val files = savedFiles()
                val repo = TestExportRepository().apply { answerSave(ExportSaveResult.Saved(files)) }
                val vm = sut(repo = repo, monitor = online())

                vm.save(randomEnum(), bookWithEveryFile())

                vm.uiState.value.share?.files shouldBe files
                vm.uiState.value.saving.shouldBeNull()
            }

            it("#118 TC-24: clears the share only for the current request") {
                val repo = TestExportRepository().apply {
                    answerSave(ExportSaveResult.Saved(savedFiles()))
                    answerSave(ExportSaveResult.Saved(savedFiles()))
                }
                val vm = sut(repo = repo, monitor = online())
                vm.save(randomEnum(), bookWithEveryFile())
                val first = checkNotNull(vm.uiState.value.share)
                vm.save(randomEnum(), bookWithEveryFile())
                val second = checkNotNull(vm.uiState.value.share)

                vm.shareShown(first.eventId)
                vm.uiState.value.share shouldBe second

                vm.shareShown(second.eventId)
                vm.uiState.value.share.shouldBeNull()
            }
        }

        context("when the save fails") {
            it("#118 TC-25: maps each failure to its error") {
                checkAll(
                    Exhaustive.of(
                        ExportSaveResult.Forbidden to ExportSaveError.Forbidden,
                        ExportSaveResult.NotFound to ExportSaveError.NotFound,
                        ExportSaveResult.Unreachable to ExportSaveError.Unreachable,
                        ExportSaveResult.NoSpace to ExportSaveError.NoSpace,
                        ExportSaveResult.Failed to ExportSaveError.Failed,
                        ExportSaveResult.NoServer to ExportSaveError.Failed,
                    ),
                ) { (result, expected) ->
                    val repo = TestExportRepository().apply { answerSave(result) }
                    val vm = sut(repo = repo, monitor = online())

                    vm.save(randomEnum(), bookWithEveryFile())

                    vm.uiState.value.saveError shouldBe expected
                    vm.uiState.value.saving.shouldBeNull()
                }
            }
        }

        context("when the device goes offline during a save") {
            it("#118 TC-27: still requests the share") {
                val files = savedFiles()
                val repo = TestExportRepository()
                val gate = repo.holdSave()
                val monitor = online()
                val vm = sut(repo = repo, monitor = monitor)
                vm.save(randomEnum(), bookWithEveryFile())

                monitor.isOnline.value = false
                gate.complete(ExportSaveResult.Saved(files))

                vm.uiState.value.share?.files shouldBe files
            }
        }
    }
})
