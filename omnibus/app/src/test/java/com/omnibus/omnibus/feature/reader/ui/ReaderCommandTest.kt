package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.feature.reader.ReaderCommand
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly

class ReaderCommandTest : DescribeSpec({

    describe("applying a command to the page") {
        it("TC-67: runs a display on the attached page") {
            val scripts = mutableListOf<String>()
            val controller = ReaderController().apply { attach { scripts += it } }
            val target = randomString()

            controller.execute(ReaderCommand.Display(target))

            scripts shouldContainExactly listOf(ReaderScripts.display(target))
        }

        it("TC-107: runs a seek on a ready page") {
            val scripts = mutableListOf<String>()
            val controller = ReaderController().apply { attach { scripts += it } }
            controller.handle(ReaderEvent.Status("ready"))

            controller.execute(ReaderCommand.Seek(0.25))

            scripts shouldContainExactly listOf(ReaderScripts.seek(0.25, 1))
        }
    }
})
