package com.omnibus.omnibus.data.libraries

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LibraryHostTest : DescribeSpec({

    describe("naming a library from its address") {
        context("when the address states no port") {
            it("TC-27: is the host when the address states no port") {
                LibraryHost.of("https://example.com") shouldBe "example.com"
                LibraryHost.of("https://example.com/library/path") shouldBe "example.com"
                LibraryHost.of("example.com") shouldBe "example.com"
            }
        }

        context("when the address states a port") {
            it("TC-28: adds the port when the address states one") {
                LibraryHost.of("example.com:8080") shouldBe "example.com:8080"
                LibraryHost.of("http://example.com:80") shouldBe "example.com:80"
                LibraryHost.of("https://user@example.com:9000/x") shouldBe "example.com:9000"
            }
        }

        context("when the address is an IPv6 host") {
            it("TC-29: keeps the brackets with no port") {
                LibraryHost.of("https://[2001:db8::1]/") shouldBe "[2001:db8::1]"
            }

            it("TC-29: keeps the brackets and adds a stated port") {
                LibraryHost.of("https://[2001:db8::1]:8443/") shouldBe "[2001:db8::1]:8443"
            }
        }

        context("when the address does not parse") {
            it("TC-29: returns the trimmed input") {
                LibraryHost.of("  not a url  ") shouldBe "not a url"
            }
        }
    }
})
