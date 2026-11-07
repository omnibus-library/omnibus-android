package com.omnibus.omnibus.data.quotecard

import com.omnibus.omnibus.data.export.TestExportFileStore
import com.omnibus.omnibus.data.export.local.ExportFileStore
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withMockServer
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import okhttp3.HttpUrl

/** Runs [block] with a temp directory, removed afterwards. */
private inline fun <T> withTempDirectory(block: (File) -> T): T {
    val directory = Files.createTempDirectory("quote-card").toFile()
    return try {
        block(directory)
    } finally {
        directory.deleteRecursively()
    }
}

/** Runs [block] with a [TestExportFileStore] under a temp directory. */
private inline fun <T> withStore(block: (TestExportFileStore) -> T): T =
    withTempDirectory { directory -> block(TestExportFileStore(File(directory, "store"))) }

class DefaultQuoteCardRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    // Reading pairs never touches the store, so the default points at a folder that doesn't exist.
    fun sut(
        baseUrl: HttpUrl? = null,
        token: String = randomString(),
        exportFileStore: ExportFileStore = TestExportFileStore(File(randomString())),
    ): DefaultQuoteCardRepository = DefaultQuoteCardRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(baseUrl, token),
        exportFileStore = exportFileStore,
    )

    describe("saving a card") {

        it("writes the bytes it was given") {
            withStore { store ->
                val png = randomString().toByteArray()

                val file = sut(exportFileStore = store).saveCard(png).shouldNotBeNull()

                file.readBytes().toList() shouldBe png.toList()
            }
        }

        it("names the file omnibus-quote.png") {
            withStore { store ->
                sut(exportFileStore = store).saveCard(randomString().toByteArray()).shouldNotBeNull().name shouldBe
                    "omnibus-quote.png"
            }
        }

        it("writes the file into a folder from the export store") {
            withStore { store ->
                val file = sut(exportFileStore = store).saveCard(randomString().toByteArray()).shouldNotBeNull()

                file.parentFile shouldBe store.folders.single()
            }
        }

        context("when a second card is saved") {
            it("gives it a folder of its own") {
                withStore { store ->
                    val repository = sut(exportFileStore = store)

                    val first = repository.saveCard(randomString().toByteArray()).shouldNotBeNull()
                    val second = repository.saveCard(randomString().toByteArray()).shouldNotBeNull()

                    second.parentFile shouldNotBe first.parentFile
                }
            }

            it("leaves the first card's bytes alone") {
                withStore { store ->
                    val repository = sut(exportFileStore = store)
                    val png = randomString().toByteArray()
                    val first = repository.saveCard(png).shouldNotBeNull()

                    repository.saveCard(randomString().toByteArray())

                    first.readBytes().toList() shouldBe png.toList()
                }
            }
        }

        context("when the export folder can't be written") {
            it("returns null") {
                withTempDirectory { directory ->
                    val root = File(directory, "store").apply { writeText("a file, not a folder") }

                    sut(exportFileStore = TestExportFileStore(root)).saveCard(randomString().toByteArray()).shouldBeNull()
                }
            }
        }
    }

    describe("reading the cover's gradient pairs") {

        context("when the server has the book's palette") {
            it("returns its pairs in the server's order") {
                val pairs = List(3) { randomCoverPair() }

                withMockServer(reply(200, paletteJson(pairs))) { server ->
                    sut(server.url("/")).getCoverPairs(randomString()) shouldBe pairs
                }
            }

            it("asks for the palette beneath the book's cover") {
                val uuid = randomString()

                withMockServer(reply(200, paletteJson(emptyList()))) { server ->
                    sut(server.url("/")).getCoverPairs(uuid)

                    server.nextRequest().url.encodedPath shouldBe "/api/books/$uuid/cover/palette"
                }
            }

            it("reads it with a GET") {
                withMockServer(reply(200, paletteJson(emptyList()))) { server ->
                    sut(server.url("/")).getCoverPairs(randomString())

                    server.nextRequest().method shouldBe "GET"
                }
            }

            it("signs the request with the saved token") {
                val token = randomString()

                withMockServer(reply(200, paletteJson(emptyList()))) { server ->
                    sut(server.url("/"), token).getCoverPairs(randomString())

                    server.nextRequest().headers["Authorization"] shouldBe "Bearer $token"
                }
            }
        }

        context("when the book's uuid has characters a path can't hold") {
            it("sends it as one encoded path segment") {
                withMockServer(reply(200, paletteJson(emptyList()))) { server ->
                    sut(server.url("/")).getCoverPairs("book 1/é")

                    server.nextRequest().url.encodedPath shouldBe "/api/books/book%201%2F%C3%A9/cover/palette"
                }
            }
        }

        listOf(
            "a 404, as an older server without the endpoint does" to reply(404),
            "a 500" to reply(500, "boom"),
            "a body that isn't JSON" to reply(200, "<html>sign in</html>"),
        ).forEach { (answer, response) ->
            context("when the server answers with $answer") {
                it("returns no pairs") {
                    withMockServer(response) { server ->
                        sut(server.url("/")).getCoverPairs(randomString()).shouldBeEmpty()
                    }
                }
            }
        }

        context("when the server drops the connection") {
            it("returns no pairs") {
                withScriptedServer {
                    script.answer(dropped())

                    sut(url).getCoverPairs(randomString()).shouldBeEmpty()
                }
            }
        }

        context("when the server can't be reached") {
            it("returns no pairs") {
                sut(unreachableUrl()).getCoverPairs(randomString()).shouldBeEmpty()
            }
        }

        context("when no server is saved") {
            it("returns no pairs") {
                sut(baseUrl = null).getCoverPairs(randomString()).shouldBeEmpty()
            }
        }

        context("when the book's uuid is blank") {
            it("returns no pairs, even from a server that has some") {
                withMockServer(reply(200, paletteJson(List(2) { randomCoverPair() }))) { server ->
                    sut(server.url("/")).getCoverPairs(" ".repeat(randomInt(0..4))).shouldBeEmpty()
                }
            }

            it("makes no request") {
                withMockServer { server ->
                    sut(server.url("/")).getCoverPairs(" ".repeat(randomInt(0..4)))

                    server.requestCount shouldBe 0
                }
            }
        }
    }
})
