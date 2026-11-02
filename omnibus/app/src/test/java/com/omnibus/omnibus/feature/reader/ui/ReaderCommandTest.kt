package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.feature.reader.ReaderCommand
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly

class ReaderCommandTest : DescribeSpec({

    describe("ReaderController.execute") {

        it("TC-67: display runs the display script on the attached page") {
            val scripts = mutableListOf<String>()
            val controller = ReaderController().apply { attach { scripts += it } }

            controller.execute(ReaderCommand.Display("chapter2.xhtml"))

            scripts shouldContainExactly listOf(ReaderScripts.display("chapter2.xhtml"))
        }
    }

    describe("seek") {

        it("TC-107: seek commands reach the controller") {
            val scripts = mutableListOf<String>()
            val controller = ReaderController().apply { attach { scripts += it } }
            ReaderEvent.decode(ReaderMessage("status", "ready"))?.let(controller::handle)

            controller.execute(ReaderCommand.Seek(0.25))

            scripts shouldContainExactly listOf(ReaderScripts.seek(0.25, 1))
        }
    }
})
