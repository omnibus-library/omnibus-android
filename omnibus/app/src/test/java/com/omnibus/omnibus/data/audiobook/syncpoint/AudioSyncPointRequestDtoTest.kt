package com.omnibus.omnibus.data.audiobook.syncpoint

import com.omnibus.omnibus.data.audiobook.syncpoint.remote.AudioSyncPointRequestDto
import com.omnibus.omnibus.data.audiobook.syncpoint.remote.SyncPointJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AudioSyncPointRequestDtoTest : DescribeSpec({

    describe("the sync point body") {

        // TC47, I-BMK-3: the defaults and the null ebook fraction are written, with the snake_case field names
        it("TC47: writes format audio and an explicit null ebook fraction") {
            val dto = AudioSyncPointRequestDto(bookUuid = "abc-123", audioBookFileId = 42L, audioSeconds = 3725.5)

            val json = SyncPointJson.encodeToString(dto)
            val fields = SyncPointJson.parseToJsonElement(json).jsonObject

            fields.keys shouldBe setOf("book_uuid", "format", "ebook_fraction", "audio_book_file_id", "audio_seconds")
            fields["format"]!!.jsonPrimitive.content shouldBe "audio"
            fields["ebook_fraction"] shouldBe JsonNull
            fields["audio_book_file_id"]!!.jsonPrimitive.content shouldBe "42"
            fields["audio_seconds"]!!.jsonPrimitive.content shouldBe "3725.5"
        }
    }
})
