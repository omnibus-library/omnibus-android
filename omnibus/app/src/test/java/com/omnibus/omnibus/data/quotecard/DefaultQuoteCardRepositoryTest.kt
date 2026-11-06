package com.omnibus.omnibus.data.quotecard

import com.omnibus.omnibus.data.export.local.DefaultExportFileStore
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers

class DefaultQuoteCardRepositoryTest : DescribeSpec({

    beforeSpec { mockAndroidLog() }
    afterSpec { unmockAndroidLog() }

    lateinit var tempDir: File

    beforeEach { tempDir = Files.createTempDirectory("quote-card").toFile() }

    afterEach { tempDir.deleteRecursively() }

    fun repository(root: File) = DefaultQuoteCardRepository(Dispatchers.Unconfined, DefaultExportFileStore(root))

    describe("DefaultQuoteCardRepository.saveCard") {

        it("returns omnibus-quote.png holding exactly the bytes, in a new folder under the export root") {
            val root = File(tempDir, "book-exports")
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 1, 2, 3)

            val file = repository(root).saveCard(png).shouldNotBeNull()

            file.name shouldBe "omnibus-quote.png"
            file.readBytes().toList() shouldBe png.toList()
            file.parentFile!!.parentFile shouldBe root
        }

        it("writes each card to its own folder") {
            val repository = repository(File(tempDir, "book-exports"))

            val first = repository.saveCard(byteArrayOf(1)).shouldNotBeNull()
            val second = repository.saveCard(byteArrayOf(2)).shouldNotBeNull()

            first.parentFile shouldNotBe second.parentFile
            first.readBytes().toList() shouldBe listOf<Byte>(1)
        }

        it("returns null when the export folder can't be written") {
            val root = File(tempDir, "book-exports").apply { writeText("a file, not a folder") }

            repository(root).saveCard(byteArrayOf(1)).shouldBeNull()
        }
    }
})
