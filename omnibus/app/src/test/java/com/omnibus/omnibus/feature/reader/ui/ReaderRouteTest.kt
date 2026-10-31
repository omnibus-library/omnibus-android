package com.omnibus.omnibus.feature.reader.ui

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import androidx.navigation3.runtime.NavKey

class ReaderRouteTest : DescribeSpec({

    describe("ReaderRoute") {

        it("TC-57: round-trips through serialization with its bookUuid") {
            val route = ReaderRoute(bookUuid = "book-1")

            val decoded = Json.decodeFromString(
                ReaderRoute.serializer(),
                Json.encodeToString(ReaderRoute.serializer(), route),
            )

            decoded shouldBe route
        }

        it("TC-57: is a NavKey") {
            val key: NavKey = ReaderRoute(bookUuid = "book-1")

            key shouldBe ReaderRoute(bookUuid = "book-1")
        }
    }
})
