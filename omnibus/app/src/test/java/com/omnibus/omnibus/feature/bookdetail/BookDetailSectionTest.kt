package com.omnibus.omnibus.feature.bookdetail

import androidx.compose.ui.graphics.Color
import com.omnibus.omnibus.data.library.testBook
import com.omnibus.omnibus.feature.bookdetail.stage.BookAccent
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class BookDetailSectionTest : DescribeSpec({

    describe("BookDetailSection contract") {

        it("#93 TC-38: C1 contract shape") {
            val context = BookDetailSectionContext(
                book = testBook(1),
                accent = BookAccent(tint = Color.Gray, onTint = Color.White, tintSoft = Color.Gray),
                reloadKey = 0,
                tabs = InertBookDetailTabController,
                onNavigate = {},
                onShowMessage = {},
                onBookChanged = {},
                onReloadBook = {},
                onClose = {},
            )

            val message = BookDetailMessage("t")
            message.text shouldBe "t"
            message.actionLabel.shouldBeNull()
            message.onAction.shouldBeNull()
            message.holdUntilDismissed shouldBe false

            val section = BookDetailSection { _, _ -> }
            section.shouldBeInstanceOf<BookDetailSection>()

            val bumped = context.copy(reloadKey = 2)
            bumped.reloadKey shouldBe 2
            bumped.book shouldBe context.book
            bumped.accent shouldBe context.accent
            bumped.tabs shouldBe context.tabs
            context.reloadKey shouldBe 0
        }
    }
})
