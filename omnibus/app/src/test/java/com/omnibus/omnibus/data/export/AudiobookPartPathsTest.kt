package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.remote.ExportManifestDto
import com.omnibus.omnibus.data.export.remote.ExportManifestPartDto
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class AudiobookPartPathsTest : DescribeSpec({

    fun parts(vararg ordinals: Long) = ordinals.map { ExportManifestPartDto(ordinal = it) }

    describe("audiobookDownloads") {

        it("#118 TC-34: a direct multi-part manifest yields one download per part in manifest order, numbered by position") {
            audiobookDownloads(ExportManifestDto(mode = "direct", parts = parts(1, 2, 3))) shouldBe listOf(
                AudiobookDownload(1, 1),
                AudiobookDownload(2, 2),
                AudiobookDownload(3, 3),
            )
            audiobookDownloads(ExportManifestDto(mode = "direct", parts = parts(3, 1, 2))) shouldBe listOf(
                AudiobookDownload(3, 1),
                AudiobookDownload(1, 2),
                AudiobookDownload(2, 3),
            )
        }

        it("#118 TC-35: a single part, an hls book, an empty or unknown mode each yield one whole-book download") {
            val cases: List<ExportManifestDto> = listOf(
                ExportManifestDto(mode = "direct", parts = parts(1)),
                ExportManifestDto(mode = "hls", parts = emptyList()),
                ExportManifestDto(mode = "hls", parts = parts(1, 2)),
                ExportManifestDto(mode = "direct", parts = emptyList()),
                ExportManifestDto(mode = "weird", parts = parts(1, 2)),
                ExportManifestDto(mode = "", parts = parts(1, 2)),
            )
            cases.forEach { manifest ->
                withClue("manifest=$manifest") {
                    audiobookDownloads(manifest) shouldBe listOf(AudiobookDownload(null, null))
                }
            }
        }
    }
})
