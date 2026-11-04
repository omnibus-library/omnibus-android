package com.omnibus.omnibus.data.bookprogress

import com.omnibus.omnibus.data.bookprogress.remote.AudiobookManifestDto
import com.omnibus.omnibus.data.bookprogress.remote.BookProgressJson
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class AudiobookManifestDtoTest : DescribeSpec({

    describe("decoding") {

        // TC-19
        it("decodes a direct body (I-LOAD-3)") {
            val body = """{"mode":"direct","book_file_id":7,"total_duration_seconds":300.5,
                "chapters":[{"ordinal":1,"title":"One","start_seconds":0.0,"duration_seconds":100.0}],
                "parts":[{"ordinal":2,"url":"/api/audiobooks/book-1/parts/2?file_id=7","duration_seconds":200.5,"mime":"audio/mpeg"},
                  {"ordinal":1,"url":"/api/audiobooks/book-1/parts/1?file_id=7","duration_seconds":100.0,"mime":"audio/mp4"}],
                "unknown_key":{"nested":true}}"""

            val dto = BookProgressJson.decodeFromString<AudiobookManifestDto>(body)

            dto.mode shouldBe "direct"
            dto.bookFileId shouldBe 7L
            dto.totalDurationSeconds shouldBe 300.5
            dto.chapters.map { it.title } shouldBe listOf("One")
            dto.parts.map { it.ordinal } shouldBe listOf(2L, 1L)
            dto.parts[0].url shouldBe "/api/audiobooks/book-1/parts/2?file_id=7"
            dto.parts[0].durationSeconds shouldBe 200.5
            dto.parts[1].mime shouldBe "audio/mp4"
            dto.playlistUrl.shouldBeNull()
        }

        // TC-19
        it("decodes an hls body (I-LOAD-3)") {
            val body = """{"mode":"hls","book_file_id":9,"playlist_url":"/api/audiobooks/book-1/playlist.m3u8"}"""

            val dto = BookProgressJson.decodeFromString<AudiobookManifestDto>(body)

            dto.mode shouldBe "hls"
            dto.bookFileId shouldBe 9L
            dto.playlistUrl shouldBe "/api/audiobooks/book-1/playlist.m3u8"
            dto.parts shouldBe emptyList()
            dto.chapters shouldBe emptyList()
        }

        // TC-19
        it("old bodies keep decoding (AND-RUSE-1)") {
            val body = """{"mode":"file","book_file_id":8,"total_duration_seconds":2000.0,
                "chapters":[{"ordinal":1,"title":"A","start_seconds":0.0,"duration_seconds":500.0}]}"""

            val dto = BookProgressJson.decodeFromString<AudiobookManifestDto>(body)

            dto.bookFileId shouldBe 8L
            dto.totalDurationSeconds shouldBe 2000.0
            dto.chapters.size shouldBe 1
            dto.parts shouldBe emptyList()
            dto.playlistUrl.shouldBeNull()
        }
    }
})
