package com.omnibus.omnibus.feature.libraries

import com.omnibus.omnibus.data.libraries.randomLibrary
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection

class LibraryLabelsTest : DescribeSpec({

    describe("displaying a library's name") {
        context("when the library has a name") {
            it("TC-3: is the name") {
                val name = randomString()

                randomLibrary().copy(name = name, address = "https://books.home.lan:8443").displayName() shouldBe name
            }
        }

        context("when the name is blank") {
            context("when the address states no port") {
                it("TC-3: is the host") {
                    checkAll(Exhaustive.collection(listOf("", "   "))) { blank ->
                        randomLibrary().copy(name = blank, address = "https://books.home.lan/").displayName() shouldBe
                            "books.home.lan"
                    }
                }
            }

            context("when the address states a port") {
                it("TC-3: is the host with the stated port") {
                    checkAll(Exhaustive.collection(listOf("", "   "))) { blank ->
                        randomLibrary().copy(name = blank, address = "https://books.home.lan:8443").displayName() shouldBe
                            "books.home.lan:8443"
                    }
                }
            }
        }
    }

    describe("the account line") {
        context("when the username is known") {
            context("when the address states a port") {
                it("TC-4: is the username, a middle dot and the host with its port") {
                    randomLibrary()
                        .copy(username = "hoon", address = "https://books.home.lan:8443")
                        .accountLine() shouldBe "hoon · books.home.lan:8443"
                }
            }

            context("when the address states no port") {
                it("TC-4: is the username, a middle dot and the host") {
                    randomLibrary()
                        .copy(username = "hoon", address = "https://books.home.lan/")
                        .accountLine() shouldBe "hoon · books.home.lan"
                }
            }
        }

        context("when the username is null") {
            it("TC-5: is the host alone, with no separator") {
                randomLibrary()
                    .copy(username = null, address = "https://books.home.lan:8443")
                    .accountLine() shouldBe "books.home.lan:8443"
            }
        }
    }
})
