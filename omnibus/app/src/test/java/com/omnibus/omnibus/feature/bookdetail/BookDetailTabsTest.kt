package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

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

        it("#93 TC-49 (updated by #94): anchor modifier adds one element, equal per anchor") {
            val anchored = Modifier.bookDetailAnchor(BookDetailAnchor.Series)
            anchored.foldIn(0) { n, _ -> n + 1 } shouldBe 1
            anchored shouldBe Modifier.bookDetailAnchor(BookDetailAnchor.Series)
            anchored.hashCode() shouldBe Modifier.bookDetailAnchor(BookDetailAnchor.Series).hashCode()
            anchored shouldNotBe Modifier

            val padded = Modifier.padding(1.dp).bookDetailAnchor(BookDetailAnchor.Series)
            padded.foldIn(0) { n, _ -> n + 1 } shouldBe 2
        }
    }
})
