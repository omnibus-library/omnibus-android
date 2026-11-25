package com.omnibus.omnibus.data.configuration

import com.omnibus.omnibus.data.libraries.LibrariesRepository
import com.omnibus.omnibus.data.libraries.TestLibrariesRepository
import com.omnibus.omnibus.data.libraries.randomLibrary
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

/** A repository whose active library has [address]. */
private fun librariesWithActive(address: String): LibrariesRepository =
    TestLibrariesRepository(listOf(randomLibrary().copy(address = address, isActive = true)))

class SavedServerBaseUrlProviderTest : DescribeSpec({

    fun sut(libraries: LibrariesRepository = TestLibrariesRepository()): SavedServerBaseUrlProvider =
        SavedServerBaseUrlProvider(libraries)

    describe("reading the base URL") {
        context("when no library is active") {
            it("TC-40: is null") {
                sut().baseUrl().shouldBeNull()
            }
        }

        context("when a library is active") {
            it("TC-40: is that library's server") {
                val address = randomServerAddress()

                sut(librariesWithActive(address)).baseUrl().toString() shouldBe "$address/"
            }
        }

        context("when an older build saved the address as typed") {
            it("TC-40: normalizes it") {
                sut(librariesWithActive(" 10.0.0.2:3000/ ")).baseUrl().toString() shouldBe "http://10.0.0.2:3000/"
            }
        }

        context("when the saved value isn't a valid server") {
            it("TC-40: is null for empty, blank, spaced and wrong-scheme values") {
                checkAll(Exhaustive.of("", "   ", "exa mple.com", "ftp://example.com")) { saved ->
                    sut(librariesWithActive(saved)).baseUrl().shouldBeNull()
                }
            }
        }
    }
})
