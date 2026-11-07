package com.omnibus.omnibus.data.household

import com.omnibus.omnibus.data.network.nextRequest
import com.omnibus.omnibus.data.network.reply
import com.omnibus.omnibus.data.network.testOmnibusHttpClient
import com.omnibus.omnibus.data.network.withMockServer
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.Dispatchers
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect

class DefaultHouseholdRepositoryTest : DescribeSpec({

    fun sut(server: MockWebServer? = null) = DefaultHouseholdRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(server?.url("/")),
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading the household") {

        it("TC-21: maps each reader, with the last session in milliseconds") {
            withMockServer { server ->
                server.enqueue(
                    reply(
                        200,
                        """[{"id":3,"name":"Maren","has_avatar":true,"seconds":22320,"sessions":4,"last_session_at":1761000000},
                            {"id":4,"name":"Theo","has_avatar":false,"seconds":7260,"sessions":2,"last_session_at":1761775445}]""",
                    ),
                )

                sut(server).getHousehold(randomString()) shouldBe HouseholdResult.Success(
                    listOf(
                        HouseholdReader(3, "Maren", true, 22_320, 4, 1_761_000_000_000L),
                        HouseholdReader(4, "Theo", false, 7_260, 2, 1_761_775_445_000L),
                    ),
                )
            }
        }

        it("TC-22: asks for the book's household") {
            withMockServer { server ->
                server.enqueue(reply(200, "[]"))
                val uuid = randomString()

                sut(server).getHousehold(uuid)

                server.nextRequest().url.encodedPath shouldBe "/api/books/$uuid/household"
            }
        }

        context("when no reader has spent time in the book") {
            it("TC-23: is a success with no readers") {
                withMockServer { server ->
                    server.enqueue(reply(200, "[]"))

                    sut(server).getHousehold(randomString()) shouldBe HouseholdResult.Success(emptyList())
                }
            }
        }

        context("when a row does not identify a reader") {
            it("TC-24: drops the row") {
                withMockServer { server ->
                    server.enqueue(
                        reply(200, """[{"name":"No id"},{"id":5,"name":"  "},{"id":6,"name":"Priya","extra":true}]"""),
                    )

                    sut(server).getHousehold(randomString()) shouldBe HouseholdResult.Success(
                        listOf(HouseholdReader(6, "Priya", false, 0, 0, null)),
                    )
                }
            }
        }

        context("when the last session is zero") {
            it("TC-24: reads as no last session") {
                withMockServer { server ->
                    server.enqueue(reply(200, """[{"id":6,"name":"Priya","last_session_at":0}]"""))

                    sut(server).getHousehold(randomString()) shouldBe HouseholdResult.Success(
                        listOf(HouseholdReader(6, "Priya", false, 0, 0, null)),
                    )
                }
            }
        }

        context("when the server answers 404") {
            it("TC-25: is NotFound") {
                withMockServer { server ->
                    server.enqueue(reply(404))

                    sut(server).getHousehold(randomString()) shouldBe HouseholdResult.NotFound
                }
            }
        }

        context("when the read fails another way") {
            it("TC-25, TC-26: is Failed") {
                checkAll(
                    Exhaustive.of(
                        reply(500),
                        reply(200, "not json"),
                        reply(200, "{\"id\":1}"),
                        MockResponse.Builder().onRequestStart(SocketEffect.CloseSocket()).build(),
                    ),
                ) { answer ->
                    withMockServer { server ->
                        server.enqueue(answer)

                        sut(server).getHousehold(randomString()) shouldBe HouseholdResult.Failed
                    }
                }
            }
        }

        context("when the uuid is blank") {
            it("TC-27: is NotFound") {
                withMockServer { server ->
                    sut(server).getHousehold(" ") shouldBe HouseholdResult.NotFound
                }
            }

            it("TC-27: sends no request") {
                withMockServer { server ->
                    sut(server).getHousehold(" ")

                    server.requestCount shouldBe 0
                }
            }
        }

        context("when no server is saved") {
            it("TC-27: is NotFound") {
                sut().getHousehold(randomString()) shouldBe HouseholdResult.NotFound
            }
        }
    }

    describe("loading an avatar") {

        it("TC-28: asks for the reader's avatar") {
            withMockServer { server ->
                server.enqueue(reply(404))
                val userId = randomLong()

                sut(server).getAvatar(userId, randomInt(1..512))

                server.nextRequest().url.encodedPath shouldBe "/api/users/$userId/avatar"
            }
        }

        context("when the request fails") {
            it("TC-28: gives no avatar") {
                withMockServer { server ->
                    server.enqueue(reply(404))

                    sut(server).getAvatar(randomLong(), randomInt(1..512)) shouldBe null
                }
            }
        }

        context("when no server is saved") {
            it("TC-29: gives no avatar") {
                sut().getAvatar(randomLong(), randomInt(1..512)) shouldBe null
            }
        }
    }
})
