package com.omnibus.omnibus.data.libraryconfiguration.remote

import com.omnibus.omnibus.data.libraryconfiguration.remote.ServerValidationRequestDto
import com.omnibus.omnibus.data.libraryconfiguration.remote.ServerValidationResponseDto
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.SerializationException

class ServerValidationDtoTest : DescribeSpec({

    describe("ServerValidationRequestDto.toRequest") {

        it("GETs the health path on the server") {
            val request = ServerValidationRequestDto("https://read.example.com").toRequest()

            request.method shouldBe "GET"
            request.url.toString() shouldBe "https://read.example.com/api/_health"
        }

        it("ignores a trailing slash") {
            ServerValidationRequestDto("https://read.example.com/").toRequest()
                .url.toString() shouldBe "https://read.example.com/api/_health"
        }

        it("keeps a reverse-proxy path prefix") {
            ServerValidationRequestDto("https://example.com/omnibus").toRequest()
                .url.toString() shouldBe "https://example.com/omnibus/api/_health"
        }

        it("keeps a port") {
            ServerValidationRequestDto("http://192.168.1.10:8080").toRequest()
                .url.toString() shouldBe "http://192.168.1.10:8080/api/_health"
        }

        listOf("read.example.com", "", "ftp://read.example.com").forEach { url ->
            it("throws for \"$url\"") {
                shouldThrow<IllegalArgumentException> {
                    ServerValidationRequestDto(url).toRequest()
                }
            }
        }
    }

    describe("ServerValidationResponseDto.fromJsonString") {

        it("parses the full health payload") {
            ServerValidationResponseDto.fromJsonString(FULL_BODY) shouldBe ServerValidationResponseDto(
                app = "omnibus",
                status = "ok",
                buildId = "1759766400123456789",
                version = "0.42.0",
                time = 1759766400,
                repoRoot = "/srv/omnibus",
            )
        }

        it("tolerates unknown fields") {
            val body = """{"app": "omnibus", "status": "ok", "uptime": 12}"""

            ServerValidationResponseDto.fromJsonString(body).isHealthyOmnibusServer.shouldBeTrue()
        }

        it("defaults the optional fields when absent") {
            val response = ServerValidationResponseDto.fromJsonString("""{"app": "omnibus", "status": "ok"}""")

            response.version.shouldBeNull()
            response.time.shouldBeNull()
        }

        listOf("", "<html></html>", """{"status": "ok"}""").forEach { body ->
            it("throws for \"$body\"") {
                shouldThrow<SerializationException> {
                    ServerValidationResponseDto.fromJsonString(body)
                }
            }
        }
    }

    describe("ServerValidationResponseDto.isHealthyOmnibusServer") {

        it("is true for an ok Omnibus server") {
            ServerValidationResponseDto(app = "omnibus", status = "ok").isHealthyOmnibusServer.shouldBeTrue()
        }

        it("is false for a different app") {
            ServerValidationResponseDto(app = "jellyfin", status = "ok").isHealthyOmnibusServer.shouldBeFalse()
        }

        it("is false when the status isn't ok") {
            ServerValidationResponseDto(app = "omnibus", status = "degraded").isHealthyOmnibusServer.shouldBeFalse()
        }
    }
}) {
    private companion object {
        const val FULL_BODY = """
            {
                "app": "omnibus",
                "status": "ok",
                "build_id": "1759766400123456789",
                "repo_root": "/srv/omnibus",
                "version": "0.42.0",
                "time": 1759766400
            }
        """
    }
}
