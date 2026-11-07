package com.omnibus.omnibus.feature.bookdetail

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.testing.randomDouble
import com.omnibus.omnibus.testing.randomLong
import com.omnibus.omnibus.testing.randomString
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

private fun randomPlaceholderTitles() = PlaceholderTitles(checkIn = randomString(), settings = randomString())

/** Records where a dispatched destination went. */
private class Opened {
    val pushed = mutableListOf<NavKey>()
    val readers = mutableListOf<Pair<String, String?>>()
    val players = mutableListOf<Triple<String, Long?, Double?>>()
    val uris = mutableListOf<String>()
}

private fun BookDetailDestination.dispatched(
    titles: PlaceholderTitles = randomPlaceholderTitles(),
): Opened = Opened().also { opened ->
    dispatch(
        top = null,
        titles = titles,
        push = { opened.pushed += it },
        openReader = { uuid, cfi -> opened.readers += uuid to cfi },
        openPlayer = { uuid, fileId, seconds -> opened.players += Triple(uuid, fileId, seconds) },
        openUri = { opened.uris += it },
    )
}

class BookDetailDestinationTest : DescribeSpec({

    describe("the route a destination pushes") {

        context("when the destination is something to browse") {
            it("#93 TC-43: is a placeholder with its name, for author, series, genre or tag") {
                val name = randomString()

                checkAll(
                    Exhaustive.of(
                        BookDetailDestination.Author(randomLong(), name),
                        BookDetailDestination.Series(null, name),
                        BookDetailDestination.Genre(name),
                        BookDetailDestination.Tag(name),
                    ),
                ) { destination ->
                    destination.shellRoute(randomPlaceholderTitles()) shouldBe BookDetailPlaceholderRoute(name)
                }
            }
        }

        context("when the destination has no screen yet") {
            it("#93 TC-44: is a placeholder with its title, for check in or settings") {
                val titles = randomPlaceholderTitles()

                BookDetailDestination.CheckIn(randomString()).shellRoute(titles) shouldBe
                    BookDetailPlaceholderRoute(titles.checkIn)
                BookDetailDestination.Settings.shellRoute(titles) shouldBe BookDetailPlaceholderRoute(titles.settings)
            }
        }

        context("when the destination opens outside the shell") {
            it("#93 TC-45 / #165 TC-6 / #165 TC-8: is none, for reader, player or external url") {
                checkAll(
                    Exhaustive.of(
                        BookDetailDestination.Reader(randomString()),
                        BookDetailDestination.Player(randomString(), randomLong(), randomDouble()),
                        BookDetailDestination.ExternalUrl(randomString()),
                    ),
                ) { destination ->
                    destination.shellRoute(randomPlaceholderTitles()).shouldBeNull()
                }
            }
        }

        context("when the route is already on top") {
            it("#93 TC-46: pushes nothing") {
                val uuid = randomString()

                BookDetailDestination.Book(uuid).pushTarget(BookDetailRoute(uuid), randomPlaceholderTitles()).shouldBeNull()
            }
        }
    }

    describe("dispatching a destination") {

        context("when the destination is the player") {
            it("#117 TC-56 / #165 TC-7: opens it with its file and position, pushing nothing") {
                val uuid = randomString()
                val fileId = randomLong()
                val seconds = randomDouble()

                val opened = BookDetailDestination.Player(uuid, fileId, seconds).dispatched()

                opened.players shouldContainExactly listOf(Triple(uuid, fileId, seconds))
                opened.pushed.shouldBeEmpty()
            }
        }

        context("when the destination is the reader") {
            it("#117 TC-57: opens it at the start cfi") {
                val uuid = randomString()
                val cfi = randomString()

                BookDetailDestination.Reader(uuid, cfi).dispatched().readers shouldContainExactly listOf(uuid to cfi)
            }
        }

        context("when the destination is an external url") {
            it("#93 TC-134: opens the url") {
                val url = randomString()

                BookDetailDestination.ExternalUrl(url).dispatched().uris shouldContainExactly listOf(url)
            }

            context("when no app can open it") {
                it("#93 TC-134: swallows the failure") {
                    shouldNotThrowAny {
                        BookDetailDestination.ExternalUrl(randomString()).dispatch(
                            top = null,
                            titles = randomPlaceholderTitles(),
                            push = {},
                            openReader = { _, _ -> },
                            openPlayer = { _, _, _ -> },
                            openUri = { error("no app can open this") },
                        )
                    }
                }
            }
        }

        context("when the destination has a shell route") {
            it("#93 TC-134: pushes it") {
                val titles = randomPlaceholderTitles()

                BookDetailDestination.Settings.dispatched(titles).pushed shouldContainExactly
                    listOf(BookDetailPlaceholderRoute(titles.settings))
            }
        }
    }
})
