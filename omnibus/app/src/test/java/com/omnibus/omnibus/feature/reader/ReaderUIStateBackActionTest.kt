package com.omnibus.omnibus.feature.reader

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class ReaderUIStateBackActionTest : DescribeSpec({

    describe("what back does") {
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
