package com.omnibus.omnibus.feature.bookdetail.overview.description

import com.omnibus.omnibus.testing.randomInt
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of

class DescriptionTextTest : DescribeSpec({
    describe("turning a description's HTML into text") {
        it("#94 TC-1: removes inline tags") {
            stripDescriptionHtml("<b>Bold</b> and <i>it</i><a href=\"x\">link</a>") shouldBe "Bold and itlink"
        }

        it("#94 TC-2: turns block ends into new lines, for any such tag in any case") {
            val cases = Exhaustive.of(
                "<p>One</p><p>Two</p>" to "One\n\nTwo",
                "<h2>One</h2>Two" to "One\n\nTwo",
                "One<br>Two" to "One\nTwo",
                "One<br/>Two" to "One\nTwo",
                "One<br />Two" to "One\nTwo",
                "<li>One</li><li>Two</li>" to "One\nTwo",
                "<div>One</div>Two" to "One\nTwo",
                "<BR>One</P>Two" to "One\n\nTwo",
            )

            checkAll(cases) { (html, text) -> stripDescriptionHtml(html) shouldBe text }
        }

        it("#94 TC-3: leaves at most one blank line between paragraphs") {
            stripDescriptionHtml("A<br><br><br><br>B\n\n\n\n\nC") shouldBe "A\n\nB\n\nC"
        }

        it("#94 TC-4: decodes named entities, each only once") {
            val cases = Exhaustive.of(
                "a&nbsp;b &amp; &lt; &gt; &quot; &apos;" to "a b & < > \" '",
                "&amp;lt;" to "&lt;",
            )

            checkAll(cases) { (html, text) -> stripDescriptionHtml(html) shouldBe text }
        }

        it("#94 TC-5: decodes numeric entities, leaving one that is not a code point as written") {
            val cases = Exhaustive.of(
                "&#65;&#x41;&#x2019;" to "AA’",
                "&#xZZ;" to "&#xZZ;",
                "&#;" to "&#;",
                "&#99999999999;" to "&#99999999999;",
            )

            checkAll(cases) { (html, text) -> stripDescriptionHtml(html) shouldBe text }
        }

        it("#94 TC-6: tidies the whitespace of each line") {
            stripDescriptionHtml("  a \t  b \n  c  ") shouldBe "a b\nc"
        }
    }

    describe("the description to show") {
        context("when there is nothing to read in it") {
            it("#94 TC-7: is none, for missing, blank or empty markup") {
                checkAll(Exhaustive.of(null, "", "   ", "<p></p>", "<br><br>", "&nbsp;", "<p> &nbsp; </p>")) { raw ->
                    cleanDescription(raw).shouldBeNull()
                }
            }
        }

        it("#94 TC-8: is the cleaned text") {
            cleanDescription("<p>Hello &amp; welcome</p>") shouldBe "Hello & welcome"
        }
    }

    describe("the show more toggle") {
        it("#94 TC-9: appears only past the clamp") {
            val clamp = randomInt(1..10)

            showsDescriptionToggle(clamp, clampLines = clamp) shouldBe false
            showsDescriptionToggle(clamp + 1, clampLines = clamp) shouldBe true
        }
    }
})
