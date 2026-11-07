package com.omnibus.omnibus.data.audiobook.playback

import com.omnibus.omnibus.di.TimeSource
import com.omnibus.omnibus.testing.TestTimeSource
import com.omnibus.omnibus.testing.randomLong
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.collection
import io.kotest.property.exhaustive.enum
import io.kotest.property.exhaustive.of

private val FORWARD = listOf(RemoteTransport.SkipForward, RemoteTransport.NextTrack, RemoteTransport.SeekForward)
private val BACKWARD = listOf(RemoteTransport.SkipBackward, RemoteTransport.PreviousTrack, RemoteTransport.SeekBackward)

private fun differentPairs(commands: List<RemoteTransport>) =
    commands.flatMap { first -> (commands - first).map { second -> first to second } }

private fun oppositePairs() = FORWARD.flatMap { forward -> BACKWARD.flatMap { back -> listOf(forward to back, back to forward) } }

class RemotePressFilterTest : DescribeSpec({

    fun sut(timeSource: TimeSource = TestTimeSource(randomLong())) = RemotePressFilter(timeSource)

    describe("deciding whether a remote command moves the position") {
        context("when a different command in the same direction follows within a second") {
            // TC-34
            it("moves once for any pair of commands (I-SYS-3)") {
                checkAll(Exhaustive.collection(differentPairs(FORWARD) + differentPairs(BACKWARD))) { (first, second) ->
                    val clock = TestTimeSource(randomLong())
                    val filter = sut(clock)

                    filter.shouldMove(first)
                    clock.advanceBy(999L)

                    filter.shouldMove(second) shouldBe false
                }
            }

            context("when a third command follows") {
                // TC-34
                it("moves again (I-SYS-3)") {
                    val filter = sut()
                    filter.shouldMove(RemoteTransport.NextTrack)
                    filter.shouldMove(RemoteTransport.SeekForward)

                    filter.shouldMove(RemoteTransport.SkipForward) shouldBe true
                }
            }
        }

        context("when a different command in the same direction follows after exactly a second") {
            // TC-34
            it("moves again (I-SYS-3)") {
                val clock = TestTimeSource(randomLong())
                val filter = sut(clock)
                filter.shouldMove(RemoteTransport.NextTrack)
                clock.advanceBy(1_000L)

                filter.shouldMove(RemoteTransport.SkipForward) shouldBe true
            }
        }

        context("when the same command repeats within a second") {
            // TC-34
            it("moves each time for any command (I-SYS-3)") {
                checkAll(Exhaustive.enum<RemoteTransport>()) { command ->
                    val filter = sut()
                    filter.shouldMove(command)

                    filter.shouldMove(command) shouldBe true
                }
            }
        }

        context("when the opposite direction follows within a second") {
            // TC-34
            it("moves for any pair of commands (I-SYS-3)") {
                checkAll(Exhaustive.collection(oppositePairs())) { (first, second) ->
                    val filter = sut()
                    filter.shouldMove(first)

                    filter.shouldMove(second) shouldBe true
                }
            }
        }

        context("when the clock has gone backwards") {
            // TC-34
            it("counts as the same press (I-SYS-3)") {
                val clock = TestTimeSource(randomLong())
                val filter = sut(clock)
                filter.shouldMove(RemoteTransport.NextTrack)
                clock.set(clock.currentMillis - 500L)

                filter.shouldMove(RemoteTransport.SkipForward) shouldBe false
            }
        }
    }

    describe("how far a remote command moves") {
        // TC-34
        it("goes forward 30 seconds for any forward command (P-PLAY-6)") {
            checkAll(Exhaustive.collection(FORWARD)) { command ->
                command.seekDeltaSeconds shouldBe 30.0
            }
        }

        // TC-34
        it("goes back 15 seconds for any backward command (P-PLAY-6)") {
            checkAll(Exhaustive.collection(BACKWARD)) { command ->
                command.seekDeltaSeconds shouldBe -15.0
            }
        }
    }

    describe("reading a media key") {
        // TC-88
        it("gives each key's command, or none for a key that is not a skip (P-PLAY-6)") {
            checkAll(
                Exhaustive.of(
                    87 to RemoteTransport.NextTrack,
                    88 to RemoteTransport.PreviousTrack,
                    89 to RemoteTransport.SeekBackward,
                    90 to RemoteTransport.SeekForward,
                    272 to RemoteTransport.SkipForward,
                    273 to RemoteTransport.SkipBackward,
                    85 to null,
                    126 to null,
                ),
            ) { (keyCode, command) ->
                RemoteTransport.forMediaKey(keyCode) shouldBe command
            }
        }

        // TC-88
        it("acts only on the first key down (P-PLAY-6)") {
            RemoteTransport.actsOn(RemoteTransport.ACTION_DOWN, 0) shouldBe true
            RemoteTransport.actsOn(1, 0) shouldBe false
            RemoteTransport.actsOn(RemoteTransport.ACTION_DOWN, 1) shouldBe false
        }
    }
})
