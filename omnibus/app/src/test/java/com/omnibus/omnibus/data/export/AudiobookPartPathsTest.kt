package com.omnibus.omnibus.data.export

import com.omnibus.omnibus.data.export.remote.ExportManifestDto
import com.omnibus.omnibus.data.export.remote.ExportManifestPartDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class AudiobookPartPathsTest : DescribeSpec({

    fun parts(vararg ordinals: Long) = ordinals.map { ExportManifestPartDto(ordinal = it) }

    describe("planning an audiobook's downloads") {
        context("when a direct manifest has several parts") {
            it("#118 TC-34: gives one download per part in manifest order, numbered by position") {
                audiobookDownloads(ExportManifestDto(mode = "direct", parts = parts(3, 1, 2))) shouldBe listOf(
                    AudiobookDownload(3, 1),
                    AudiobookDownload(1, 2),
                    AudiobookDownload(2, 3),
                )
            }
        }

        it("#118 TC-35: gives one whole-book download for a single part, an hls book, and an empty or unknown mode") {
            checkAll(
                Exhaustive.of(
                    ExportManifestDto(mode = "direct", parts = parts(1)),
                    ExportManifestDto(mode = "hls", parts = emptyList()),
                    ExportManifestDto(mode = "hls", parts = parts(1, 2)),
                    ExportManifestDto(mode = "direct", parts = emptyList()),
                    ExportManifestDto(mode = "weird", parts = parts(1, 2)),
                    ExportManifestDto(mode = "", parts = parts(1, 2)),
                ),
            ) { manifest ->
                audiobookDownloads(manifest) shouldBe listOf(AudiobookDownload(null, null))
            }
        }
    }
})
