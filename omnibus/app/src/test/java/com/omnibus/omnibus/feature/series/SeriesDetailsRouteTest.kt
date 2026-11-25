package com.omnibus.omnibus.feature.series

import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.json.Json

class SeriesDetailsRouteTest : DescribeSpec({

    describe("a series page route") {

        it("#349 TC-32: is equal to another push of the same series") {
            val id = randomLong()
            val name = randomString()

            SeriesDetailsRoute(id, name) shouldBe SeriesDetailsRoute(id, name)
        }

        it("#349 TC-32: gives each push its own content key") {
            val id = randomLong()
            val name = randomString()

            SeriesDetailsRoute(id, name).contentKey shouldNotBe SeriesDetailsRoute(id, name).contentKey
        }

        it("#349 TC-32: keeps its instance through a JSON round trip") {
            val route = SeriesDetailsRoute(randomLong(), randomString())

            val json = Json.encodeToString(SeriesDetailsRoute.serializer(), route)
            val decoded = Json.decodeFromString(SeriesDetailsRoute.serializer(), json)

            decoded.instance shouldBe route.instance
        }
    }
})
