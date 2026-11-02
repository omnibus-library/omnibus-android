package com.omnibus.omnibus.feature.bookdetail

import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class BookDetailRouteTest : DescribeSpec({

    describe("BookDetailRoute") {

        it("#93 TC-47: routes serialize") {
            Json.encodeToString(BookDetailRoute.serializer(), BookDetailRoute("u")) shouldBe """{"uuid":"u"}"""
            Json.decodeFromString(BookDetailRoute.serializer(), """{"uuid":"u"}""") shouldBe BookDetailRoute("u")

            val placeholder = BookDetailPlaceholderRoute("t")
            Json.decodeFromString(
                BookDetailPlaceholderRoute.serializer(),
                Json.encodeToString(BookDetailPlaceholderRoute.serializer(), placeholder),
            ) shouldBe placeholder

            BookDetailRoute::class.java.packageName shouldBe "com.omnibus.omnibus.feature.bookdetail.ui"
        }
    }
})
