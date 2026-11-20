package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.R
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

class HighlightMenuShareActionTest : DescribeSpec({

    describe("offering Share") {
        context("with text") {
            it("#330 TC-1: is offered for any menu kind") {
                checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                    highlightMenuActions(HighlightMenuTarget(kind, randomString())) shouldContain HighlightMenuAction.Share
                }
            }

            it("#330 TC-2: sits just before Copy, for any menu kind") {
                checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                    val actions = highlightMenuActions(HighlightMenuTarget(kind, randomString()))

                    actions.indexOf(HighlightMenuAction.Share) shouldBeGreaterThanOrEqual 0
                    actions.indexOf(HighlightMenuAction.Copy) shouldBe actions.indexOf(HighlightMenuAction.Share) + 1
                }
            }
        }

        context("without text") {
            it("#330 TC-3: is not offered for any menu kind, for text that is null, empty or blank") {
                checkAll(Exhaustive.enum<HighlightMenuKind>(), Exhaustive.of<String?>(null, "", "   ")) { kind, text ->
                    highlightMenuActions(HighlightMenuTarget(kind, text)) shouldNotContain HighlightMenuAction.Share
                }
            }
        }
    }

    describe("labelling Share") {
        it("#330 TC-4: is labelled Share, for any menu kind") {
            checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                HighlightMenuAction.Share.labelOn(HighlightMenuTarget(kind, randomString())) shouldBe R.string.reader_highlight_share
            }
        }
    }

    describe("the Share icon") {
        it("#330 TC-4: is Book Detail's export icon") {
            HighlightMenuAction.Share.icon shouldBe R.drawable.ic_book_detail_more_export
        }
    }
})
