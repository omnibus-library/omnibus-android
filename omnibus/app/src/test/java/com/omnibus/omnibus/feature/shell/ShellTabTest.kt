package com.omnibus.omnibus.feature.shell

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class ShellTabTest : DescribeSpec({

    describe("the tabs in the bar") {
        it("are the iOS client's tabs, in its order") {
            ShellTab.entries shouldBe listOf(ShellTab.Library, ShellTab.Search, ShellTab.Stats, ShellTab.You)
        }
    }
})
