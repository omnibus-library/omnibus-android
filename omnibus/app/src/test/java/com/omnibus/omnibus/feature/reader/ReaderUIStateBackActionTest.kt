package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.SelectionData
import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class ReaderUIStateBackActionTest : DescribeSpec({

    describe("what back does") {
        context("with a highlight menu open") {
            it("#299 TC-21: closes the highlight menu, ahead of the selection, the sheet, the dropdown and context mode") {
                val selections = Exhaustive.of<SelectionData?>(null, randomSelectionData())
                val sheets = Exhaustive.of<ReaderSheet?>(null, ReaderSheet.Contents, ReaderSheet.Settings)

                checkAll(selections, sheets, Exhaustive.boolean(), Exhaustive.boolean()) { selection, sheet, menuOpen, chromeVisible ->
                    val state = ReaderUIState(
                        highlightMenu = HighlightMenu(listOf(randomHighlightMenuEntry())),
                        selection = selection,
                        sheet = sheet,
                        menuOpen = menuOpen,
                        chromeVisible = chromeVisible,
                    )

                    state.backAction() shouldBe ReaderBackAction.DismissHighlightMenu
                }
            }
        }

        context("with a selection up") {
            it("#54 TC-28: dismisses the selection, ahead of the sheet, the dropdown and context mode") {
                val sheets = Exhaustive.of<ReaderSheet?>(null, ReaderSheet.Contents, ReaderSheet.Settings)

                checkAll(sheets, Exhaustive.boolean(), Exhaustive.boolean()) { sheet, menuOpen, chromeVisible ->
                    val state = ReaderUIState(
                        selection = randomSelectionData(),
                        sheet = sheet,
                        menuOpen = menuOpen,
                        chromeVisible = chromeVisible,
                    )

                    state.backAction() shouldBe ReaderBackAction.DismissSelection
                }
            }
        }

        context("with a sheet open") {
            it("TC-4: dismisses the sheet, whether or not the dropdown is open and context mode is on") {
                val sheets = Exhaustive.of(ReaderSheet.Contents, ReaderSheet.Settings)

                checkAll(sheets, Exhaustive.boolean(), Exhaustive.boolean()) { sheet, menuOpen, chromeVisible ->
                    val state = ReaderUIState(sheet = sheet, menuOpen = menuOpen, chromeVisible = chromeVisible)

                    state.backAction() shouldBe ReaderBackAction.DismissSheet
                }
            }
        }

        context("with no sheet open") {
            context("with the dropdown open") {
                it("TC-4: dismisses the dropdown, whether or not context mode is on") {
                    checkAll(Exhaustive.boolean()) { chromeVisible ->
                        ReaderUIState(menuOpen = true, chromeVisible = chromeVisible).backAction() shouldBe
                            ReaderBackAction.DismissMenu
                    }
                }
            }

            context("with the dropdown closed") {
                it("TC-4: leaves context mode while it is on") {
                    ReaderUIState(chromeVisible = true).backAction() shouldBe ReaderBackAction.LeaveContext
                }

                it("TC-4: leaves the reader while context mode is off") {
                    ReaderUIState(chromeVisible = false).backAction() shouldBe ReaderBackAction.LeaveReader
                }
            }
        }
    }
})
