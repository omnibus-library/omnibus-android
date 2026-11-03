package com.omnibus.omnibus.data.reviews.remote

import com.omnibus.omnibus.data.reviews.Patch
import com.omnibus.omnibus.data.reviews.ReviewPatch
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer

class ReviewPatchRequestTest : DescribeSpec({

    describe("the PATCH body") {

        it("TC-12: only set fields are sent, a set null is an explicit null, and the request has the JSON media type") {
            ReviewPatch().toJsonObject().toString() shouldBe "{}"

            val cleared = ReviewPatch(rating = Patch.Set(null))
            cleared.toJsonObject().toString() shouldBe """{"stars":null}"""

            val rated = ReviewPatch(rating = Patch.Set(4.5f))
            rated.toJsonObject().jsonObject["stars"]!!.jsonPrimitive.content shouldBe "4.5"

            val all = ReviewPatch(
                rating = Patch.Set(3f),
                text = Patch.Set(null),
                privateNotes = Patch.Set("n"),
                isSpoiler = Patch.Set(true),
            ).toJsonObject()
            all.keys shouldContainExactlyInAnyOrder listOf("stars", "body_md", "notes_md", "spoiler")
            all["body_md"] shouldBe JsonNull
            all["notes_md"]!!.jsonPrimitive.content shouldBe "n"
            all["spoiler"]!!.jsonPrimitive.content shouldBe "true"

            val body = ReviewPatch(rating = Patch.Set(4.5f)).toJsonRequest()
            body.contentType().toString() shouldBe "application/json; charset=utf-8"
            Buffer().also { body.writeTo(it) }.readUtf8() shouldBe """{"stars":4.5}"""
        }
    }
})
