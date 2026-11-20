package com.omnibus.omnibus.data.scan

import app.cash.turbine.test
import com.omnibus.omnibus.data.library.LibraryChanges
import com.omnibus.omnibus.data.wishlist.TestWishlistRepository
import com.omnibus.omnibus.data.wishlist.WishlistSource
import com.omnibus.omnibus.testing.dropped
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.nextRequest
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.reply
import com.omnibus.omnibus.testing.testOmnibusHttpClient
import com.omnibus.omnibus.testing.unmockAndroidLog
import com.omnibus.omnibus.testing.unreachableUrl
import com.omnibus.omnibus.testing.withScriptedServer
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.HttpUrl

/** A book as the server writes it. Null optional fields are left out. */
private fun ScanBook.toJson(): String = buildJsonObject {
    put("uuid", uuid)
    put("title", title)
    putJsonArray("authors") { authors.forEach { add(JsonPrimitive(it)) } }
    coverUrl?.let { put("cover_url", it) }
    put("has_physical", hasPhysical)
    put("has_files", hasFiles)
    isbn?.let { put("isbn", it) }
}.toString()

/** An outcome body of [kind] for [book], with a key the app doesn't model. */
private fun outcomeBody(kind: String, book: ScanBook): String =
    """{"kind":"$kind","book":${book.toJson()},"future_key":{"nested":true}}"""

/** The `BookRef` the check-in route answers with. */
private fun bookRefBody(uuid: String): String = """{"book_uuid":"$uuid"}"""

class DefaultScanRepositoryTest : DescribeSpec({

    fun sut(
        baseUrl: HttpUrl? = null,
        wishlist: TestWishlistRepository = TestWishlistRepository(),
        changes: LibraryChanges = LibraryChanges(),
    ): DefaultScanRepository = DefaultScanRepository(
        ioDispatcher = Dispatchers.Unconfined,
        omnibusHttpClient = testOmnibusHttpClient(baseUrl),
        applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        wishlistRepository = wishlist,
        libraryChanges = changes,
    )

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("looking an ISBN up") {

        it("#317 TC-21: decodes in_library_unowned to InLibraryUnowned with every book field") {
            withScriptedServer {
                val book = randomScanBook()
                script.answer("POST", reply(200, outcomeBody("in_library_unowned", book)))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.InLibraryUnowned(book))
            }
        }

        it("#317 TC-21: decodes on_wishlist to OnWishlist with every book field") {
            withScriptedServer {
                val book = randomScanBook()
                script.answer("POST", reply(200, outcomeBody("on_wishlist", book)))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.OnWishlist(book))
            }
        }

        it("#317 TC-21: decodes already_owned to AlreadyOwned with every book field") {
            withScriptedServer {
                val book = randomScanBook()
                script.answer("POST", reply(200, outcomeBody("already_owned", book)))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.AlreadyOwned(book))
            }
        }

        it("#317 TC-21: decodes not_in_library to NotInLibrary with the provider's record") {
            withScriptedServer {
                val record = randomProviderRecord()
                script.answer("POST", reply(200, """{"kind":"not_in_library","online":${record.raw},"future_key":{"nested":true}}"""))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.NotInLibrary(record))
            }
        }

        it("#336 TC-44: decodes a not-in-library answer and keeps the record whole") {
            withScriptedServer {
                val record = randomProviderRecord()
                script.answer("POST", reply(200, """{"kind":"not_in_library","online":${record.raw}}"""))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.NotInLibrary(record))
            }
        }

        it("#336 TC-44: decodes a minimal not-in-library record with its missing fields null") {
            withScriptedServer {
                val isbn = randomValidIsbn13()
                val title = randomString()
                val raw = buildJsonObject { put("isbn13", isbn); put("title", title); put("source", "open_library") }
                script.answer("POST", reply(200, """{"kind":"not_in_library","online":$raw}"""))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(
                    ScanOutcome.NotInLibrary(
                        ProviderRecord(
                            raw = raw,
                            isbn13 = isbn,
                            title = title,
                            authors = emptyList(),
                            year = null,
                            firstPublishYear = null,
                            publisher = null,
                            pages = null,
                            series = null,
                            coverUrl = null,
                        ),
                    ),
                )
            }
        }

        it("#317 TC-21: decodes close_match to Unplaced") {
            withScriptedServer {
                script.answer("POST", reply(200, outcomeBody("close_match", randomScanBook())))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.Unplaced)
            }
        }

        it("#317 TC-21: decodes unresolved to Unplaced") {
            withScriptedServer {
                script.answer("POST", reply(200, outcomeBody("unresolved", randomScanBook())))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.Unplaced)
            }
        }

        it("#317 TC-21: decodes an unknown kind to Unplaced") {
            withScriptedServer {
                script.answer("POST", reply(200, outcomeBody(randomString(), randomScanBook())))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.Unplaced)
            }
        }

        it("#317 TC-21: decodes a body with no kind to Unplaced") {
            withScriptedServer {
                script.answer("POST", reply(200, """{"book":${randomScanBook().toJson()}}"""))

                sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Success(ScanOutcome.Unplaced)
            }
        }

        it("#317 TC-22: maps each failure to its typed error") {
            withScriptedServer {
                checkAll(
                    Exhaustive.of(
                        reply(503) to ScanError.ProvidersUnreachable,
                        reply(400) to ScanError.Rejected,
                        reply(401) to ScanError.SessionExpired,
                        reply(404) to ScanError.NotFound,
                        reply(500) to ScanError.Http(500),
                        dropped() to ScanError.Unreachable,
                        reply(200, "not json") to ScanError.UnexpectedResponse,
                    ),
                ) { (response, error) ->
                    script.answer("POST", response)

                    sut(url).resolve(randomValidIsbn13()) shouldBe ScanResult.Failure(error)
                }
            }
        }

        it("#317 TC-22: is Unreachable when the server is refused") {
            sut(unreachableUrl()).resolve(randomValidIsbn13()) shouldBe ScanResult.Failure(ScanError.Unreachable)
        }

        it("#317 TC-22: is NoServer when no server is saved") {
            sut(null).resolve(randomValidIsbn13()) shouldBe ScanResult.NoServer
        }

        it("#317 TC-23: posts the ISBN as JSON to api/scan/resolve") {
            withScriptedServer {
                val isbn = randomValidIsbn13()
                script.answer("POST", reply(200, outcomeBody("unresolved", randomScanBook())))

                sut(url).resolve(isbn)

                nextRequest().let { Triple(it.method, it.url.encodedPath, Json.parseToJsonElement(it.body!!.utf8())) } shouldBe
                    Triple("POST", "/api/scan/resolve", buildJsonObject { put("isbn", isbn) })
            }
        }
    }

    describe("checking a copy in") {

        it("#317 TC-23: posts the book, the ISBN and the note to api/scan/check-in") {
            withScriptedServer {
                val uuid = randomString()
                val isbn = randomValidIsbn13()
                val note = randomString()
                script.answer("POST", reply(200, bookRefBody(uuid)))

                sut(url).checkIn(uuid, isbn, note)

                nextRequest().let { Triple(it.method, it.url.encodedPath, Json.parseToJsonElement(it.body!!.utf8())) } shouldBe
                    Triple(
                        "POST",
                        "/api/scan/check-in",
                        buildJsonObject { put("book_uuid", uuid); put("isbn", isbn); put("note", note) },
                    )
            }
        }

        it("#317 TC-23: leaves a null note out of the check-in body") {
            withScriptedServer {
                val uuid = randomString()
                val isbn = randomValidIsbn13()
                script.answer("POST", reply(200, bookRefBody(uuid)))

                sut(url).checkIn(uuid, isbn, null)

                nextRequest().let { Json.parseToJsonElement(it.body!!.utf8()) } shouldBe
                    buildJsonObject { put("book_uuid", uuid); put("isbn", isbn) }
            }
        }

        it("#317 TC-23: returns the book_uuid the server wrote to") {
            withScriptedServer {
                val uuid = randomString()
                script.answer("POST", reply(200, bookRefBody(uuid)))

                sut(url).checkIn(randomString(), randomValidIsbn13(), null) shouldBe ScanResult.Success(uuid)
            }
        }

        it("#317 TC-33: forgets the wishlist state of the book it checked in and of the one the server wrote to") {
            withScriptedServer {
                val requested = randomString()
                val written = randomString()
                val wishlist = TestWishlistRepository()
                script.answer("POST", reply(200, bookRefBody(written)))

                sut(url, wishlist).checkIn(requested, randomValidIsbn13(), null)

                wishlist.invalidateCalls shouldContainExactlyInAnyOrder listOf(requested, written)
            }
        }

        it("#317 TC-33: forgets the wishlist state once when the server wrote to the same book") {
            withScriptedServer {
                val uuid = randomString()
                val wishlist = TestWishlistRepository()
                script.answer("POST", reply(200, bookRefBody(uuid)))

                sut(url, wishlist).checkIn(uuid, randomValidIsbn13(), null)

                wishlist.invalidateCalls shouldBe listOf(uuid)
            }
        }

        it("#317 TC-33: forgets no wishlist state when the check-in fails") {
            withScriptedServer {
                val wishlist = TestWishlistRepository()
                script.answer("POST", reply(400))

                sut(url, wishlist).checkIn(randomString(), randomValidIsbn13(), null)

                wishlist.invalidateCalls shouldBe emptyList()
            }
        }
    }

    describe("adding a book the library doesn't have") {

        it("#336 TC-47: sends the record back untouched to physical-only") {
            withScriptedServer {
                val record = randomProviderRecord()
                val uuid = randomString()
                script.answer("POST", reply(200, bookRefBody(uuid)))

                sut(url).addPhysicalOnly(record) shouldBe ScanResult.Success(uuid)

                nextRequest().let { Triple(it.method, it.url.encodedPath, Json.parseToJsonElement(it.body!!.utf8())) } shouldBe
                    Triple("POST", "/api/scan/physical-only", buildJsonObject { put("meta", record.raw) })
            }
        }

        it("#336 TC-47: sends the record back untouched to the wishlist with the source token") {
            withScriptedServer {
                checkAll(Exhaustive.enum<WishlistSource>()) { source ->
                    val record = randomProviderRecord()
                    script.answer("POST", reply(200, bookRefBody(randomString())))

                    sut(url).addToWishlist(record, source)

                    nextRequest().let { Triple(it.method, it.url.encodedPath, Json.parseToJsonElement(it.body!!.utf8())) } shouldBe
                        Triple(
                            "POST",
                            "/api/scan/wishlist",
                            buildJsonObject { put("meta", record.raw); put("source", source.name.lowercase()) },
                        )
                }
            }
        }

        it("#336 TC-52: tells the library after each successful add") {
            withScriptedServer {
                val changes = LibraryChanges()
                script.answer("POST", reply(200, bookRefBody(randomString())))
                script.answer("POST", reply(200, bookRefBody(randomString())))
                val repository = sut(url, changes = changes)

                changes.changes.test {
                    repository.addPhysicalOnly(randomProviderRecord())
                    awaitItem()
                    repository.addToWishlist(randomProviderRecord(), WishlistSource.Scan)
                    awaitItem()
                    expectNoEvents()
                }
            }
        }

        it("#336 TC-52: tells the library nothing about a failed add") {
            withScriptedServer {
                val changes = LibraryChanges()
                script.answer("POST", reply(400))

                changes.changes.test {
                    sut(url, changes = changes).addToWishlist(randomProviderRecord(), WishlistSource.Manual) shouldBe
                        ScanResult.Failure(ScanError.Rejected)
                    expectNoEvents()
                }
            }
        }
    }
})
