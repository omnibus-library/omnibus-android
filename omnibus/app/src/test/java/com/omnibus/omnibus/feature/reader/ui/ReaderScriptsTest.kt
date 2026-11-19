package com.omnibus.omnibus.feature.reader.ui

import com.omnibus.omnibus.data.highlights.HighlightColor
import com.omnibus.omnibus.data.reader.ReaderMargins
import com.omnibus.omnibus.data.reader.ReaderPageTurn
import com.omnibus.omnibus.data.reader.ReaderSettings
import com.omnibus.omnibus.data.reader.ReaderTheme
import com.omnibus.omnibus.data.reader.ReaderTypeface
import com.omnibus.omnibus.data.reader.randomReaderSettings
import com.omnibus.omnibus.feature.reader.PageHighlight
import com.omnibus.omnibus.feature.reader.randomPageHighlight
import com.omnibus.omnibus.testing.randomInt
import com.omnibus.omnibus.testing.randomString
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.property.Arb
import io.kotest.property.Exhaustive
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** A CFI that holds a quote, a backslash and a newline, which a script could misread. */
private const val SPECIAL_CFI = "epubcfi(/6/4[id\"x'\\y]\n!/4)"

class ReaderScriptsTest : DescribeSpec({

    /** Splits `OmnibusReader.init('column', "<url>", {…})` back into its URL and options. */
    fun parseBoot(script: String): Pair<String, JsonObject> {
        val arguments = script.removePrefix("OmnibusReader.init('column', ").removeSuffix(")")
        val url = Json.parseToJsonElement(arguments.substringBefore(", {")).jsonPrimitive.content
        val options = Json.parseToJsonElement("{" + arguments.substringAfter(", {")).jsonObject
        return url to options
    }

    fun boot(
        bookUuid: String = randomString(),
        settings: ReaderSettings = randomReaderSettings(),
        cfi: String? = randomCfi(),
    ) = parseBoot(ReaderScripts.boot(bookUuid, settings, cfi))

    describe("booting the page") {
        it("TC-228: points init at the book on the asset-loader origin") {
            val uuid = randomString()

            boot(bookUuid = uuid).first shouldBe "https://appassets.androidplatform.net/book/$uuid.epub"
        }

        it("TC-229: carries the settings, start position, scripted content and locations key") {
            val uuid = randomString()
            val settings = randomReaderSettings()
            val cfi = randomCfi()

            val (_, options) = boot(uuid, settings, cfi)

            options["theme"]!!.jsonPrimitive.content shouldBe settings.theme.token
            options["fontSize"]!!.jsonPrimitive.int shouldBe settings.fontSize
            options["lineHeight"]!!.jsonPrimitive.double shouldBe settings.lineHeight
            options["justify"]!!.jsonPrimitive.boolean shouldBe settings.justify
            options["spread"]!!.jsonPrimitive.content shouldBe settings.spread
            options["allowScriptedContent"]!!.jsonPrimitive.boolean shouldBe true
            options["locationsKey"]!!.jsonPrimitive.content shouldBe uuid
            options["cfi"]!!.jsonPrimitive.content shouldBe cfi
        }

        context("without a start position") {
            it("TC-230: leaves out the cfi, for a null or blank one") {
                checkAll(Exhaustive.of(null, " ")) { cfi ->
                    boot(cfi = cfi).second.containsKey("cfi") shouldBe false
                }
            }
        }

        it("sends each typeface's stack, and none for the book's own") {
            checkAll(Exhaustive.enum<ReaderTypeface>()) { face ->
                val settings = randomReaderSettings().copy(typeface = face)

                boot(settings = settings).second["fontFamily"]?.jsonPrimitive?.content shouldBe face.css
            }
        }

        context("with a cfi that holds characters a script would misread") {
            it("TC-231: quotes the cfi") {
                val cfi = "epubcfi(/6/4[id\"x']!/4)"

                boot(cfi = cfi).second["cfi"]!!.jsonPrimitive.content shouldBe cfi
            }
        }
    }

    describe("applying a settings change") {
        context("when nothing differs") {
            it("TC-232: sends nothing") {
                val settings = randomReaderSettings()

                ReaderScripts.settingsChange(settings, settings).shouldBeEmpty()
            }
        }

        context("when several settings differ") {
            it("TC-233: sends one call per changed setting") {
                val shown = ReaderSettings()
                val wanted = shown.copy(fontSize = 24, theme = ReaderTheme.Light, lineHeight = 2.0, justify = false)

                ReaderScripts.settingsChange(shown, wanted).shouldContainExactlyInAnyOrder(
                    "OmnibusReader.setFontSize(24)",
                    "OmnibusReader.setTheme(\"light\")",
                    "OmnibusReader.setLineHeight(2.0)",
                    "OmnibusReader.setJustify(false)",
                )
            }
        }

        context("when the typeface differs") {
            it("TC-234: sends the face as a quoted stack, or null for the book's own") {
                val original = randomReaderSettings().copy(typeface = ReaderTypeface.Original)
                val classic = original.copy(typeface = ReaderTypeface.Classic)

                ReaderScripts.settingsChange(original, classic)
                    .shouldContainExactly("OmnibusReader.setFont(\"'EB Garamond','Noto Serif',serif\")")
                ReaderScripts.settingsChange(classic, original).shouldContainExactly("OmnibusReader.setFont(null)")
            }
        }

        context("when only the margins differ") {
            it("TC-29: sends nothing, for any pair of margins") {
                val settings = randomReaderSettings()

                checkAll(Exhaustive.enum<ReaderMargins>(), Exhaustive.enum<ReaderMargins>()) { from, to ->
                    ReaderScripts.settingsChange(settings.copy(margins = from), settings.copy(margins = to)).shouldBeEmpty()
                }
            }
        }

        context("when only the page turn differs") {
            it("TC-304: sends nothing, for any pair of page turns") {
                val settings = randomReaderSettings()

                checkAll(Exhaustive.enum<ReaderPageTurn>(), Exhaustive.enum<ReaderPageTurn>()) { from, to ->
                    val changed = ReaderScripts.settingsChange(settings.copy(pageTurn = from), settings.copy(pageTurn = to))
                    changed.shouldBeEmpty()
                }
            }
        }
    }

    describe("styling the page") {
        fun insets() = PageInsets(randomInt(0..100), randomInt(0..100), randomInt(0..100), randomInt(0..100))

        it("TC-29: hands over the margin as a quoted --measure, for every margin") {
            checkAll(Exhaustive.enum<ReaderMargins>()) { margins ->
                ReaderScripts.pageStyle(randomString(), insets(), margins.css) shouldContain
                    "root.style.setProperty('--measure', \"${margins.css}\");"
            }
        }

        it("hands over the ground and the insets") {
            val ground = randomString()
            val insets = insets()

            val script = ReaderScripts.pageStyle(ground, insets, randomString())

            script shouldContain "setProperty('--page', \"$ground\")"
            script shouldContain "setProperty('--inset-top', '${insets.top}px')"
            script shouldContain "setProperty('--inset-bottom', '${insets.bottom}px')"
        }
    }

    describe("seeking") {
        context("in a locale with a decimal comma") {
            it("TC-92: writes the number with a point") {
                val previous = Locale.getDefault()
                try {
                    Locale.setDefault(Locale.GERMANY)
                    ReaderScripts.seek(0.57, 3) shouldBe "OmnibusReader.seek(0.57, 3)"
                } finally {
                    Locale.setDefault(previous)
                }
            }
        }
    }

    describe("reconciling the highlights drawn on the page") {
        context("when a range is wanted but not drawn") {
            it("#54 TC-4: adds it") {
                val added = randomPageHighlight()

                ReaderScripts.highlightsChange(emptyList(), listOf(added)).shouldContainExactly(
                    ReaderScripts.addAnnotation(added.cfiRange, added.color, added.hasNote),
                )
            }
        }

        context("when a range is drawn but no longer wanted") {
            it("#54 TC-4: removes it") {
                val gone = randomPageHighlight()

                ReaderScripts.highlightsChange(listOf(gone), emptyList())
                    .shouldContainExactly(ReaderScripts.removeAnnotation(gone.cfiRange))
            }
        }

        context("when a drawn range changes colour") {
            it("#54 TC-4: removes it, then adds it in the new colour") {
                val drawn = PageHighlight(randomCfi(), HighlightColor.Amber, hasNote = false)
                val wanted = drawn.copy(color = HighlightColor.Blue)

                ReaderScripts.highlightsChange(listOf(drawn), listOf(wanted)).shouldContainExactly(
                    ReaderScripts.removeAnnotation(drawn.cfiRange),
                    ReaderScripts.addAnnotation(drawn.cfiRange, HighlightColor.Blue, hasNote = false),
                )
            }
        }

        context("when a drawn range gains a note") {
            it("#54 TC-4: removes it, then adds it with the note") {
                val drawn = PageHighlight(randomCfi(), HighlightColor.Amber, hasNote = false)
                val wanted = drawn.copy(hasNote = true)

                ReaderScripts.highlightsChange(listOf(drawn), listOf(wanted)).shouldContainExactly(
                    ReaderScripts.removeAnnotation(drawn.cfiRange),
                    ReaderScripts.addAnnotation(drawn.cfiRange, HighlightColor.Amber, hasNote = true),
                )
            }
        }

        context("when a new range is wanted beside a range that is drawn unchanged") {
            it("#54 TC-4: sends only the new range") {
                val kept = randomPageHighlight()
                val added = randomPageHighlight()

                ReaderScripts.highlightsChange(listOf(kept), listOf(kept, added)).shouldContainExactly(
                    ReaderScripts.addAnnotation(added.cfiRange, added.color, added.hasNote),
                )
            }
        }

        context("when the drawn and wanted rows are equal") {
            it("#54 TC-4: sends nothing, for any number of rows") {
                checkAll(Arb.int(0..6)) { size ->
                    val rows = List(size) { randomPageHighlight() }

                    ReaderScripts.highlightsChange(rows, rows).shouldBeEmpty()
                }
            }
        }
    }

    describe("the page calls that draw and remove highlights") {
        /** The string a call's first argument quotes, read back from the script. */
        fun firstArgument(call: String): String =
            Json.parseToJsonElement(call.substringAfter("(").removeSuffix(")").substringBefore(", ")).jsonPrimitive.content

        context("when the cfi holds quotes, a backslash and a newline") {
            it("#54 TC-5: passes it to addAnnotation as one string") {
                val call = ReaderScripts.addAnnotation(SPECIAL_CFI, HighlightColor.Amber, hasNote = true)

                firstArgument(call) shouldBe SPECIAL_CFI
            }

            it("#54 TC-5: passes it to removeAnnotation as one string") {
                firstArgument(ReaderScripts.removeAnnotation(SPECIAL_CFI)) shouldBe SPECIAL_CFI
            }
        }

        context("for each highlight colour") {
            it("#54 TC-5: sends the colour as the glue's lowercase key") {
                val glueKeys = mapOf(
                    HighlightColor.Amber to "amber",
                    HighlightColor.Green to "green",
                    HighlightColor.Blue to "blue",
                    HighlightColor.Rose to "rose",
                    HighlightColor.Violet to "violet",
                )
                val cfi = randomCfi()

                checkAll(Exhaustive.enum<HighlightColor>()) { color ->
                    ReaderScripts.addAnnotation(cfi, color, hasNote = false) shouldBe
                        "OmnibusReader.addAnnotation(\"$cfi\", \"${glueKeys.getValue(color)}\", false)"
                }
            }
        }

        context("when a handle is dragged from either edge") {
            it("#54 TC-5: sends the edge's name") {
                checkAll(Exhaustive.of(SelectionEdge.Start to "start", SelectionEdge.End to "end")) { (edge, name) ->
                    ReaderScripts.beginEdgeDrag(edge) shouldBe "OmnibusReader.beginEdgeDrag(\"$name\")"
                }
            }
        }

        context("when the selection is extended to a finger position") {
            it("#54 TC-5: sends the finger's x as the third argument") {
                ReaderScripts.extendSelectionTo(12.5, 340.25, 7.75) shouldBe
                    "OmnibusReader.extendSelectionTo(12.5, 340.25, 7.75)"
            }
        }
    }
})
