package com.omnibus.omnibus.feature.bookdetail

import androidx.navigation3.runtime.NavKey
import com.omnibus.omnibus.feature.bookdetail.stub.BookDetailPlaceholderRoute
import com.omnibus.omnibus.feature.bookdetail.ui.BookDetailRoute
import com.omnibus.omnibus.feature.library.ui.PlayerStubRoute
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class BookDetailDestinationTest : DescribeSpec({

    val titles = PlaceholderTitles(checkIn = "Check in", settings = "Settings")

    describe("BookDetailDestination routing") {

        it("#93 TC-42: player and book routes") {
            BookDetailDestination.Player("b", 9).shellRoute(titles) shouldBe PlayerStubRoute("b", 9)
            BookDetailDestination.Book("other").shellRoute(titles) shouldBe BookDetailRoute("other")
        }

        it("#93 TC-43: browse destinations go to the placeholder") {
            BookDetailDestination.Author(3, "Ursula K. Le Guin").shellRoute(titles) shouldBe
                BookDetailPlaceholderRoute("Ursula K. Le Guin")
            BookDetailDestination.Series(null, "Earthsea").shellRoute(titles) shouldBe
                BookDetailPlaceholderRoute("Earthsea")
            BookDetailDestination.Genre("Fantasy").shellRoute(titles) shouldBe BookDetailPlaceholderRoute("Fantasy")
            BookDetailDestination.Tag("cozy").shellRoute(titles) shouldBe BookDetailPlaceholderRoute("cozy")
        }

        it("#93 TC-44: check in and settings titles") {
            BookDetailDestination.CheckIn("u").shellRoute(titles) shouldBe
                BookDetailPlaceholderRoute(titles.checkIn)
            BookDetailDestination.Settings.shellRoute(titles) shouldBe BookDetailPlaceholderRoute(titles.settings)
        }

        it("#93 TC-45: reader and external url are not pushed") {
            BookDetailDestination.Reader("u").shellRoute(titles).shouldBeNull()
            BookDetailDestination.ExternalUrl("https://x").shellRoute(titles).shouldBeNull()

            // Exhaustive on purpose: a new destination must be named here too.
            val kinds = listOf(
                BookDetailDestination.Reader("u"),
                BookDetailDestination.Player("u", null),
                BookDetailDestination.Book("u"),
                BookDetailDestination.Author(1, "a"),
                BookDetailDestination.Series(null, "s"),
                BookDetailDestination.Genre("g"),
                BookDetailDestination.Tag("t"),
                BookDetailDestination.CheckIn("u"),
                BookDetailDestination.Settings,
                BookDetailDestination.ExternalUrl("https://x"),
            ).map { destination ->
                when (destination) {
                    is BookDetailDestination.Reader -> "Reader"
                    is BookDetailDestination.Player -> "Player"
                    is BookDetailDestination.Book -> "Book"
                    is BookDetailDestination.Author -> "Author"
                    is BookDetailDestination.Series -> "Series"
                    is BookDetailDestination.Genre -> "Genre"
                    is BookDetailDestination.Tag -> "Tag"
                    is BookDetailDestination.CheckIn -> "CheckIn"
                    BookDetailDestination.Settings -> "Settings"
                    is BookDetailDestination.ExternalUrl -> "ExternalUrl"
                }
            }
            kinds shouldContainExactly listOf(
                "Reader", "Player", "Book", "Author", "Series", "Genre", "Tag", "CheckIn", "Settings", "ExternalUrl",
            )
        }

        it("#93 TC-46: a push equal to the top route is ignored") {
            val top = BookDetailRoute("a")
            BookDetailDestination.Book("a").pushTarget(top, titles).shouldBeNull()
            BookDetailDestination.Book("b").pushTarget(top, titles) shouldBe BookDetailRoute("b")

            BookDetailDestination.Book("a").pushTarget(BookDetailRoute("b"), titles) shouldBe BookDetailRoute("a")

            val placeholderTop: NavKey = BookDetailPlaceholderRoute("X")
            BookDetailDestination.Genre("X").pushTarget(placeholderTop, titles).shouldBeNull()
        }

        it("#117 TC-57: reader dispatch carries the start cfi") {
            val opened = mutableListOf<Pair<String, String?>>()
            fun dispatch(destination: BookDetailDestination) = destination.dispatch(
                top = null,
                titles = titles,
                push = {},
                openReader = { uuid, cfi -> opened += uuid to cfi },
                openUri = {},
            )

            dispatch(BookDetailDestination.Reader("u"))
            dispatch(BookDetailDestination.Reader("u", "epubcfi(/6/8)"))
            opened shouldContainExactly listOf("u" to null, "u" to "epubcfi(/6/8)")
        }

        it("#93 TC-134: dispatch routes each destination") {
            val pushed = mutableListOf<NavKey>()
            val readers = mutableListOf<String>()
            val uris = mutableListOf<String>()
            fun dispatch(destination: BookDetailDestination, top: NavKey? = null) = destination.dispatch(
                top = top,
                titles = titles,
                push = { pushed += it },
                openReader = { uuid, _ -> readers += uuid },
                openUri = { uris += it },
            )

            dispatch(BookDetailDestination.Reader("u"))
            readers shouldContainExactly listOf("u")
            pushed.shouldBeEmpty()
            uris.shouldBeEmpty()

            dispatch(BookDetailDestination.ExternalUrl("https://x"))
            uris shouldContainExactly listOf("https://x")

            pushed.clear()
            dispatch(BookDetailDestination.Settings)
            pushed shouldContainExactly listOf(BookDetailPlaceholderRoute(titles.settings))

            pushed.clear()
            dispatch(BookDetailDestination.Book("top"), top = BookDetailRoute("top"))
            pushed.shouldBeEmpty()

            // An openUri that throws is swallowed: the call returns normally.
            BookDetailDestination.ExternalUrl("https://y").dispatch(
                top = null,
                titles = titles,
                push = {},
                openReader = { _, _ -> },
                openUri = { error("no app can open this") },
            )
        }
    }
})
