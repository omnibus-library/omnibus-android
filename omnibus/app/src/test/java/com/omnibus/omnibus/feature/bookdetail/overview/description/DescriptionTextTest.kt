package com.omnibus.omnibus.feature.bookdetail.overview.description

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

class DescriptionTextTest : DescribeSpec({

    describe("description text") {

        it("#94 TC-1: stripDescriptionHtml removes inline tags") {
            val out = stripDescriptionHtml("<b>Bold</b> and <i>it</i><a href=\"x\">link</a>")
            out shouldBe "Bold and itlink"
            out shouldNotContain "<"
            out shouldNotContain ">"
        }

        it("#94 TC-2: block tags and br become line breaks") {
            stripDescriptionHtml("<p>One</p><p>Two</p>") shouldBe "One\n\nTwo"
            stripDescriptionHtml("A<br>B") shouldBe "A\nB"
            stripDescriptionHtml("A<br/>B") shouldBe "A\nB"
            stripDescriptionHtml("A<br />B") shouldBe "A\nB"
            stripDescriptionHtml("<li>A</li><li>B</li>") shouldBe "A\nB"
            stripDescriptionHtml("<div>A</div>B") shouldBe "A\nB"
            stripDescriptionHtml("<h2>One</h2>Two") shouldBe "One\n\nTwo"
            stripDescriptionHtml("<BR>A</P>B") shouldBe "A\n\nB"
        }

        it("#94 TC-3: at most one blank line between paragraphs") {
            stripDescriptionHtml("A<br><br><br><br>B") shouldBe "A\n\nB"
            stripDescriptionHtml("A\n\n\n\n\nB") shouldBe "A\n\nB"
        }

        it("#94 TC-4: named entities are decoded, amp last") {
            stripDescriptionHtml("a&nbsp;b &amp; &lt; &gt; &quot; &apos; &#39;") shouldBe "a b & < > \" ' '"
            stripDescriptionHtml("&amp;lt;") shouldBe "&lt;"
        }

        it("#94 TC-5: numeric entities are decoded") {
            stripDescriptionHtml("&#65;") shouldBe "A"
            stripDescriptionHtml("&#x41;") shouldBe "A"
            stripDescriptionHtml("&#x2019;") shouldBe "’"
            stripDescriptionHtml("&#xZZ;") shouldBe "&#xZZ;"
            stripDescriptionHtml("&#;") shouldBe "&#;"
            stripDescriptionHtml("&#99999999999;") shouldBe "&#99999999999;"
        }

        it("#94 TC-6: whitespace runs are collapsed and lines trimmed") {
            stripDescriptionHtml("  a \t  b \n  c  ") shouldBe "a b\nc"
        }

        it("#94 TC-7: cleanDescription returns null for null and blank input") {
            listOf(null, "", "   ", "<p></p>", "<br><br>", "&nbsp;", "<p> &nbsp; </p>").forEach { raw ->
                cleanDescription(raw) shouldBe null
            }
        }

        it("#94 TC-8: cleanDescription returns cleaned text") {
            cleanDescription("<p>Hello &amp; welcome</p>") shouldBe "Hello & welcome"
            cleanDescription("Plain") shouldBe "Plain"
        }

        it("#94 TC-9: toggle shows only above the clamp") {
            DESCRIPTION_CLAMP_LINES shouldBe 3
            listOf(0, 1, 2, 3).forEach { showsDescriptionToggle(it) shouldBe false }
            listOf(4, 5, 100).forEach { showsDescriptionToggle(it) shouldBe true }
            showsDescriptionToggle(5, clampLines = 5) shouldBe false
            showsDescriptionToggle(6, clampLines = 5) shouldBe true
        }
    }
})
