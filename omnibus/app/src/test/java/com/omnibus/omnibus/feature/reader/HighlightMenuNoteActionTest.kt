package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.R
import com.omnibus.omnibus.testing.randomBoolean
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

class HighlightMenuNoteActionTest : DescribeSpec({

    describe("offering the note action") {
        it("#331 TC-5: is offered first on every menu kind, whatever the text") {
            val texts = Exhaustive.of<String?>(null, "", "   ", randomString())
            checkAll(Exhaustive.enum<HighlightMenuKind>(), texts) { kind, text ->
                highlightMenuActions(HighlightMenuTarget(kind, text)).firstOrNull() shouldBe HighlightMenuAction.Note
            }
        }

        it("#331 TC-5: is offered on a highlight that already has a note") {
            checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                val target = HighlightMenuTarget(kind, randomString(), hasNote = true)
                highlightMenuActions(target) shouldContain HighlightMenuAction.Note
            }
        }
    }

    describe("describing the note action") {
        context("on a highlight with a note") {
            it("#332 TC-1: is labelled Edit note") {
                checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                    val target = HighlightMenuTarget(kind, randomString().takeIf { randomBoolean() }, hasNote = true)
                    HighlightMenuAction.Note.labelOn(target) shouldBe R.string.reader_highlight_note_edit
                }
            }
        }

        it("#331 TC-6: is labelled Add note on both menus") {
            checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                val target = HighlightMenuTarget(kind, randomString())
                HighlightMenuAction.Note.labelOn(target) shouldBe R.string.reader_highlight_note_add
            }
        }

        it("#331 TC-6: uses the note icon") {
            HighlightMenuAction.Note.icon shouldBe R.drawable.ic_reader_note
        }
    }
})
