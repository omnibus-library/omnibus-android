package com.omnibus.omnibus.data.reviews.remote

import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.ReviewPatch
import com.omnibus.omnibus.data.reviews.randomRating
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okio.Buffer

class ReviewPatchRequestTest : DescribeSpec({

    describe("the PATCH body") {

        it("TC-12: names every set field the way the server does") {
            val rating = randomRating()
            val text = randomString()
            val notes = randomString()
            val spoiler = randomBoolean()

            val patch = ReviewPatch(
                rating = Patch.Set(rating),
                text = Patch.Set(text),
                privateNotes = Patch.Set(notes),
                isSpoiler = Patch.Set(spoiler),
            )

            patch.toJsonObject() shouldBe buildJsonObject {
                put("stars", rating)
                put("body_md", text)
                put("notes_md", notes)
                put("spoiler", spoiler)
            }
        }

        it("TC-12: writes only the set fields, keeping a set null explicit") {
            checkAll(
                Exhaustive.of(
                    ReviewPatch() to "{}",
                    ReviewPatch(rating = Patch.Set(null)) to """{"stars":null}""",
                    ReviewPatch(text = Patch.Set(null)) to """{"body_md":null}""",
                    ReviewPatch(privateNotes = Patch.Set(null)) to """{"notes_md":null}""",
                ),
            ) { (patch, json) ->
                patch.toJsonObject().toString() shouldBe json
            }
        }

        it("TC-12: is sent as JSON") {
            val rating = randomRating()

            val body = ReviewPatch(rating = Patch.Set(rating)).toJsonRequest()

            body.contentType().toString() shouldBe "application/json; charset=utf-8"
            Buffer().also { body.writeTo(it) }.readUtf8() shouldBe """{"stars":${JsonPrimitive(rating)}}"""
        }
    }
})
