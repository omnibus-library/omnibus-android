package com.omnibus.omnibus.feature.reader

import com.omnibus.omnibus.feature.reader.ui.randomSelectionData
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

class HighlightMenuActionsTest : DescribeSpec({

    describe("offering Copy") {
        it("#329 TC-1: is offered for any menu kind when the text is not blank") {
            checkAll(Exhaustive.enum<HighlightMenuKind>()) { kind ->
                highlightMenuActions(HighlightMenuTarget(kind, randomString())) shouldContain HighlightMenuAction.Copy
            }
        }

        it("#329 TC-1: is not offered for any menu kind when the text is null, empty or blank") {
            checkAll(Exhaustive.enum<HighlightMenuKind>(), Exhaustive.of<String?>(null, "", "   ")) { kind, text ->
                highlightMenuActions(HighlightMenuTarget(kind, text)) shouldNotContain HighlightMenuAction.Copy
            }
        }
    }

    describe("offering Delete") {
        it("#329 TC-2: is offered on the tap menu whatever the text") {
            checkAll(Exhaustive.of<String?>(null, "", "   ", randomString())) { text ->
                highlightMenuActions(HighlightMenuTarget(HighlightMenuKind.Tap, text)) shouldContain HighlightMenuAction.Delete
            }
        }

        it("#329 TC-2: is never offered on the selection menu whatever the text") {
            checkAll(Exhaustive.of<String?>(null, "", "   ", randomString())) { text ->
                highlightMenuActions(HighlightMenuTarget(HighlightMenuKind.Create, text)) shouldNotContain HighlightMenuAction.Delete
            }
        }

        it("#329 TC-2: is the only action on a tap highlight with no text") {
            val actions = highlightMenuActions(HighlightMenuTarget(HighlightMenuKind.Tap, null))

            actions shouldContain HighlightMenuAction.Delete
            actions shouldNotContain HighlightMenuAction.Copy
        }
    }

    describe("ordering the actions") {
        it("#329 TC-3: lists Copy before Delete on the tap menu") {
            val actions = highlightMenuActions(HighlightMenuTarget(HighlightMenuKind.Tap, randomString()))

            actions.indexOf(HighlightMenuAction.Copy) shouldBeGreaterThanOrEqual 0
            actions.indexOf(HighlightMenuAction.Delete) shouldBeGreaterThanOrEqual 0
            actions.indexOf(HighlightMenuAction.Copy) shouldBeLessThan actions.indexOf(HighlightMenuAction.Delete)
        }
    }

    describe("the target of a menu") {
        it("#329 TC-4: a highlight's tap target carries its kind, text and note") {
            val entry = randomHighlightMenuEntry()

            entry.menuTarget shouldBe HighlightMenuTarget(HighlightMenuKind.Tap, entry.text, entry.hasNote)
        }

        it("#329 TC-4: a selection's target carries its kind and text, and never a note") {
            val selection = randomSelectionData()

            selection.menuTarget shouldBe HighlightMenuTarget(HighlightMenuKind.Create, selection.text, hasNote = false)
        }
    }
})
