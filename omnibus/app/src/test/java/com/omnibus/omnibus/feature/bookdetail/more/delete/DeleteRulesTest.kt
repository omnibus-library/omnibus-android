package com.omnibus.omnibus.feature.bookdetail.more.delete

import com.omnibus.omnibus.data.deletion.DeletableCopy
import com.omnibus.omnibus.data.deletion.DeletableFile
import com.omnibus.omnibus.data.deletion.DeletionImpact
import com.omnibus.omnibus.data.deletion.DeletionManifest
import com.omnibus.omnibus.data.deletion.randomDeletableCopy
import com.omnibus.omnibus.data.deletion.randomDeletableFile
import com.omnibus.omnibus.data.library.randomBook
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

private fun files(count: Int): List<DeletableFile> = List(count) { randomDeletableFile() }

private fun copies(count: Int): List<DeletableCopy> = List(count) { randomDeletableCopy() }

private fun manifestOf(
    files: List<DeletableFile> = emptyList(),
    copies: List<DeletableCopy> = emptyList(),
    impact: DeletionImpact = DeletionImpact(),
) = DeletionManifest(files = files, copies = copies, impact = impact)

private fun List<DeletableFile>.fileIds(): Set<Long> = map { it.id }.toSet()

private fun List<DeletableCopy>.copyIds(): Set<Long> = map { it.id }.toSet()

private fun confirmEverything(manifest: DeletionManifest): DeleteConfirmCopy =
    confirmCopy(manifest, manifest.files.fileIds(), manifest.copies.copyIds())

class DeleteRulesTest : DescribeSpec({

    describe("the delete row") {
        context("when the user is not an admin") {
            it("#121 TC-1: is hidden, regardless of connectivity or files") {
                checkAll(Exhaustive.boolean(), Exhaustive.boolean()) { online, hasFiles ->
                    deleteRow(isAdmin = false, isOnline = online, hasFiles = hasFiles) shouldBe DeleteRow.Hidden
                }
            }
        }

        context("when the user is an admin") {
            it("#121 TC-1: is enabled online, disabled offline, labelled from the files") {
                checkAll(
                    Exhaustive.of(
                        Triple(true, true, DeleteRow.Enabled(DeleteRowLabel.Files)),
                        Triple(true, false, DeleteRow.Enabled(DeleteRowLabel.Record)),
                        Triple(false, true, DeleteRow.DisabledOffline(DeleteRowLabel.Files)),
                        Triple(false, false, DeleteRow.DisabledOffline(DeleteRowLabel.Record)),
                    ),
                ) { (online, hasFiles, expected) ->
                    deleteRow(isAdmin = true, isOnline = online, hasFiles = hasFiles) shouldBe expected
                }
            }
        }
    }

    describe("whether a book has files") {
        context("when it has only a physical copy") {
            it("#121 TC-2: has no files") {
                hasFiles(randomBook().copy(formats = emptyList(), bookFiles = emptyList(), hasPhysical = true)) shouldBe false
            }
        }
    }

    describe("which step the sheet opens on") {
        context("when the book has something to pick") {
            it("#121 TC-3: does not open on confirm") {
                checkAll(
                    Exhaustive.of(
                        manifestOf(files = files(1)),
                        manifestOf(copies = copies(1)),
                        manifestOf(files = files(1), copies = copies(1)),
                    ),
                ) { manifest ->
                    opensOnConfirm(manifest) shouldBe false
                }
            }
        }
    }

    describe("the choose step") {
        it("#121 TC-4: counts files on disk, switching to items with copies") {
            chooseSummary(manifestOf(files = files(2))) shouldBe ChooseSummary(2, DeleteNoun.File, onDisk = true)
            chooseSummary(manifestOf(files = files(2), copies = copies(1))) shouldBe
                ChooseSummary(3, DeleteNoun.Item, onDisk = false)
        }

        it("#121 TC-4: offers its action only with a pick") {
            checkAll(
                Exhaustive.of(
                    Triple(0, false, ChooseAction.Unpicked(DeleteNoun.File)),
                    Triple(0, true, ChooseAction.Unpicked(DeleteNoun.Item)),
                    Triple(2, false, ChooseAction.Picked(2, DeleteNoun.File)),
                    Triple(2, true, ChooseAction.Picked(2, DeleteNoun.Item)),
                ),
            ) { (picked, hasCopies, expected) ->
                chooseAction(picked, hasCopies) shouldBe expected
            }
        }
    }

    describe("the confirm step") {
        context("when everything is picked") {
            it("#121 TC-5, TC-8, TC-9, TC-10, TC-11: deletes the book, naming it for a single item") {
                checkAll(
                    Exhaustive.of(
                        manifestOf(files = files(2)) to
                            Triple(ConfirmHeading.All(2, DeleteNoun.File), ConfirmBody.TotalFiles, ConfirmAction.DeleteBook),
                        manifestOf(files = files(1)) to
                            Triple(ConfirmHeading.NameBook, ConfirmBody.TotalFiles, ConfirmAction.DeleteBook),
                        manifestOf(copies = copies(1)) to
                            Triple(ConfirmHeading.NameBook, ConfirmBody.TotalCopiesOnly, ConfirmAction.DeleteBook),
                        manifestOf(copies = copies(2)) to
                            Triple(ConfirmHeading.All(2, DeleteNoun.Item), ConfirmBody.TotalCopiesOnly, ConfirmAction.DeleteBook),
                        manifestOf(files = files(2), copies = copies(1)) to
                            Triple(ConfirmHeading.All(3, DeleteNoun.Item), ConfirmBody.TotalFilesAndCopies, ConfirmAction.DeleteBook),
                        manifestOf() to
                            Triple(ConfirmHeading.NameBook, ConfirmBody.RecordOnly, ConfirmAction.DeleteRecord),
                    ),
                ) { (manifest, expected) ->
                    val (heading, body, action) = expected

                    confirmEverything(manifest) shouldBe DeleteConfirmCopy(heading, body, action, emptyList())
                }
            }
        }

        context("when only some items are picked") {
            it("#121 TC-7, TC-10, TC-11: counts what goes against what stays") {
                val threeFiles = files(3)
                val twoCopies = copies(2)
                val mixedFiles = files(2)
                val mixedCopies = copies(1)

                confirmCopy(manifestOf(threeFiles), threeFiles.take(2).fileIds(), emptySet()) shouldBe DeleteConfirmCopy(
                    ConfirmHeading.Some(2, DeleteNoun.File),
                    ConfirmBody.PartialFiles(PickedLabel.Counts(2, 0), remaining = 1, remainingNoun = DeleteNoun.File),
                    ConfirmAction.DeleteSome(2, DeleteNoun.File),
                    emptyList(),
                )
                confirmCopy(manifestOf(copies = twoCopies), emptySet(), twoCopies.take(1).copyIds()) shouldBe DeleteConfirmCopy(
                    ConfirmHeading.Some(1, DeleteNoun.Item),
                    ConfirmBody.PartialCopies(PickedLabel.Counts(0, 1), remaining = 1, remainingNoun = DeleteNoun.Item),
                    ConfirmAction.DeleteSome(1, DeleteNoun.Item),
                    emptyList(),
                )
                confirmCopy(
                    manifestOf(mixedFiles, mixedCopies),
                    mixedFiles.take(1).fileIds(),
                    mixedCopies.copyIds(),
                ) shouldBe DeleteConfirmCopy(
                    ConfirmHeading.Some(2, DeleteNoun.Item),
                    ConfirmBody.PartialMixed(PickedLabel.Counts(1, 1), remaining = 1, remainingNoun = DeleteNoun.Item),
                    ConfirmAction.DeleteSome(2, DeleteNoun.Item),
                    emptyList(),
                )
            }

            context("when the pick is one file") {
                it("#121 TC-6, TC-11: names it by its label, else its filename") {
                    checkAll(
                        Exhaustive.of<Pair<String?, String>>("Read copy" to "Read copy", " " to "f.epub", null to "f.epub"),
                    ) { (label, name) ->
                        val picked = randomDeletableFile().copy(label = label, filename = "f.epub")
                        val manifest = manifestOf(files = listOf(picked) + files(1), copies = copies(1))

                        confirmCopy(manifest, setOf(picked.id), emptySet()).body shouldBe
                            ConfirmBody.PartialFiles(PickedLabel.NamedFile(name), remaining = 2, remainingNoun = DeleteNoun.Item)
                    }
                }
            }
        }
    }

    describe("what else a delete takes") {
        it("#121 TC-12, TC-13: lists the non-zero counts in the server's order") {
            val impact = DeletionImpact(
                highlights = 4,
                journalEntries = 3,
                bookmarks = 0,
                readingSessions = 1,
                listeningSessions = 5,
                reviews = 6,
                shelves = 7,
            )

            losses(impact) shouldBe listOf(
                Loss(LossKind.Highlights, 4),
                Loss(LossKind.JournalEntries, 3),
                Loss(LossKind.ReadingSessions, 1),
                Loss(LossKind.ListeningSessions, 5),
                Loss(LossKind.Reviews, 6),
                Loss(LossKind.Shelves, 7),
            )
        }

        context("when the delete takes the whole book") {
            it("#121 TC-12, TC-13: warns of the losses, for files or a record only") {
                val impact = DeletionImpact(highlights = 1)

                checkAll(Exhaustive.of(manifestOf(files = files(1), impact = impact), manifestOf(impact = impact))) { manifest ->
                    confirmEverything(manifest).losses shouldNotBe emptyList<Loss>()
                }
            }
        }

        context("when the delete is partial") {
            it("#121 TC-13: warns of no losses") {
                val twoFiles = files(2)
                val manifest = manifestOf(twoFiles, impact = DeletionImpact(highlights = 1))

                confirmCopy(manifest, twoFiles.take(1).fileIds(), emptySet()).losses shouldBe emptyList()
            }
        }
    }

    describe("the done message") {
        it("#121 TC-14: follows the iOS wording for files, copies, or both") {
            checkAll(
                Exhaustive.of(
                    Triple(1, 0, DoneToast.File),
                    Triple(2, 0, DoneToast.Files(2)),
                    Triple(0, 1, DoneToast.Copy),
                    Triple(0, 2, DoneToast.ItemsRemoved(2)),
                    Triple(1, 1, DoneToast.ItemsDeleted(2)),
                ),
            ) { (files, copies, expected) ->
                doneToast(files, copies) shouldBe expected
            }
        }
    }
})
