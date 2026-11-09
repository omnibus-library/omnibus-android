package com.omnibus.omnibus.feature.reader.ui.curl

import com.omnibus.omnibus.feature.reader.ui.ReaderScripts
import com.omnibus.omnibus.testing.randomOf
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive

class CurlCaptureTest : DescribeSpec({

    /** A capture over a fake page and snapshotter that log to [log]; the cover logs "cover". */
    fun sut(
        log: MutableList<String> = mutableListOf(),
        page: FakePage = FakePage(log),
        snapshotter: PageSnapshotter = FakeSnapshotter(log),
    ): CurlCapture = CurlCapture(page::call, snapshotter) { log += "cover" }

    /** A page that lays out in one or two columns, as a curl needs, with its replies to the peeks scripted. */
    fun laidOutPage(log: MutableList<String>, next: String, prev: String): FakePage = FakePage(log).apply {
        replies[ReaderScripts.WHEN_SETTLED] = { settledJson(next = next, prev = prev, columns = randomOf(listOf(1, 2))) }
        replies[ReaderScripts.peek(1)] = { JsonPrimitive(true) }
        replies[ReaderScripts.peek(-1)] = { JsonPrimitive(true) }
        replies[ReaderScripts.peek(0)] = { JsonPrimitive(true) }
    }

    describe("filling the snapshots of a settled page") {
        context("when the next neighbour is a page") {
            context("when the previous neighbour is a section") {
                it("TC-320: captures the front and the next page in call order") {
                    runTest {
                        val log = mutableListOf<String>()

                        sut(log, laidOutPage(log, next = "page", prev = "section")).run(snapshots = true)

                        log shouldContainExactly listOf(
                            ReaderScripts.WHEN_SETTLED,
                            "snapshot",
                            "cover",
                            ReaderScripts.peek(1),
                            "snapshot",
                            ReaderScripts.peek(0),
                        )
                    }
                }

                it("TC-320: never peeks at the section behind") {
                    runTest {
                        val log = mutableListOf<String>()

                        sut(log, laidOutPage(log, next = "page", prev = "section")).run(snapshots = true)

                        log shouldNotContain ReaderScripts.peek(-1)
                    }
                }
            }
        }
    }

    describe("replacing the snapshot ring") {
        context("when the ring holds three images") {
            it("TC-321: recycles every image it held, once each, when cleared") {
                runTest {
                    val capture = sut()
                    val front = FakeImage("front")
                    val next = FakeImage("next")
                    val previous = FakeImage("previous")
                    capture.replaceRing(CurlRing(current = front, next = next, previous = previous))

                    capture.clear()

                    listOf(front, next, previous).map { it.recycles } shouldBe listOf(1, 1, 1)
                }
            }

            context("when a turn lands on the next page") {
                it("TC-321: recycles the image that falls off the back once") {
                    runTest {
                        val capture = sut()
                        val front = FakeImage("front")
                        val next = FakeImage("next")
                        val previous = FakeImage("previous")
                        val ring = CurlRing<PageImage>(current = front, next = next, previous = previous)
                        capture.replaceRing(ring)

                        capture.replaceRing(ring.landed(1))

                        previous.recycles shouldBe 1
                    }
                }

                it("TC-321: does not recycle the images the ring keeps") {
                    runTest {
                        val capture = sut()
                        val front = FakeImage("front")
                        val next = FakeImage("next")
                        val previous = FakeImage("previous")
                        val ring = CurlRing<PageImage>(current = front, next = next, previous = previous)
                        capture.replaceRing(ring)

                        capture.replaceRing(ring.landed(1))

                        listOf(front, next).map { it.recycles } shouldBe listOf(0, 0)
                    }
                }
            }
        }
    }
})
