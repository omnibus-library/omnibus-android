package com.omnibus.omnibus.feature.reader.note

import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.boolean
import io.kotest.property.exhaustive.of

class ReaderNoteComposerTest : DescribeSpec({

    describe("whether Save is on") {
        it("#331 TC-18: is off for blank text, whatever the saving state") {
            checkAll(Exhaustive.of("", "   ", "\n\t"), Exhaustive.of(false, true)) { text, saving ->
                NoteComposerState(randomString(), quote = null, text = text, saving = saving).canSave shouldBe false
            }
        }

        context("when the text is not blank") {
            it("#331 TC-18: is on while no save is in flight") {
                NoteComposerState(randomString(), quote = null, text = randomString()).canSave shouldBe true
            }

            it("#331 TC-18: is off while a save is in flight") {
                val state = NoteComposerState(randomString(), quote = null, text = randomString(), saving = true)
                state.canSave shouldBe false
            }
        }
    }

    describe("editing an existing note") {
        it("#332 TC-8: cannot save a note that is unchanged after trimming") {
            val note = randomString()
            checkAll(Exhaustive.of(" $note", "$note\n", "\t$note  ")) { text ->
                NoteComposerState(randomString(), quote = null, text = text, existing = note).canSave shouldBe false
            }
        }

        it("#332 TC-9: can save a changed note") {
            val note = randomString()
            NoteComposerState(randomString(), quote = null, text = "$note more", existing = note).canSave shouldBe true
        }
    }

    describe("deleting a note") {
        it("#332 TC-10: can delete only an existing note while idle") {
            val note = randomString()
            checkAll(Exhaustive.boolean(), Exhaustive.boolean()) { saving, deleting ->
                val busy = saving || deleting
                NoteComposerState(
                    randomString(),
                    quote = null,
                    text = note,
                    existing = note,
                    saving = saving,
                    deleting = deleting,
                ).canDelete shouldBe !busy
                NoteComposerState(randomString(), quote = null, text = note, saving = saving, deleting = deleting)
                    .canDelete shouldBe false
            }
        }

        it("#332 TC-10: cannot save while deleting") {
            val note = randomString()
            checkAll(Exhaustive.boolean()) { editing ->
                val state = NoteComposerState(
                    randomString(),
                    quote = null,
                    text = "$note more",
                    existing = if (editing) note else null,
                    deleting = true,
                )
                state.canSave shouldBe false
            }
        }
    }

    describe("counting the note") {
        it("#331 TC-18: counts a surrogate pair as one code point") {
            noteLength("a😀b") shouldBe 3
        }

        it("#331 TC-18: hides the counter below 3,900 code points") {
            NoteComposerState(randomString(), quote = null, text = "x".repeat(3_899)).showsCounter shouldBe false
        }

        it("#331 TC-18: shows the counter from 3,900 code points") {
            NoteComposerState(randomString(), quote = null, text = "x".repeat(3_900)).showsCounter shouldBe true
        }
    }
})
