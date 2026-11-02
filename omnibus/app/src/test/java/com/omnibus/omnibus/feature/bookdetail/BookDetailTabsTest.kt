package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs

class BookDetailTabsTest : DescribeSpec({

    describe("BookDetailTabController") {

        it("#93 TC-48: inert tab controller") {
            BookDetailTab.entries.toList() shouldBe listOf(
                BookDetailTab.Overview,
                BookDetailTab.Reviews,
                BookDetailTab.Highlights,
                BookDetailTab.Files,
            )
            BookDetailAnchor.entries.toList() shouldBe listOf(BookDetailAnchor.Series)

            val controller = InertBookDetailTabController
            controller.selected shouldBe BookDetailTab.Overview
            controller.select(BookDetailTab.Reviews)
            controller.jumpTo(BookDetailTab.Files)
            controller.scrollTo(BookDetailAnchor.Series)
            controller.selected shouldBe BookDetailTab.Overview
        }

        it("#93 TC-49: anchor modifier returns the receiver") {
            Modifier.bookDetailAnchor(BookDetailAnchor.Series) shouldBeSameInstanceAs Modifier

            val padded = Modifier.padding(1.dp)
            padded.bookDetailAnchor(BookDetailAnchor.Series) shouldBeSameInstanceAs padded
        }
    }
})
