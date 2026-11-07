package com.omnibus.omnibus.data.progress.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.omnibus.omnibus.data.library.local.FailingPreferencesDataStore
import com.omnibus.omnibus.data.library.local.TestPreferencesDataStore
import com.omnibus.omnibus.data.progress.ReadingPosition
import com.omnibus.omnibus.data.progress.randomReadingPosition
import com.omnibus.omnibus.testing.mockAndroidLog
import com.omnibus.omnibus.testing.randomString
import com.omnibus.omnibus.testing.unmockAndroidLog
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Exhaustive
import io.kotest.property.checkAll
import io.kotest.property.exhaustive.of
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList

/** Book 1's stored value is corrupt and book 2's is sound, written in the stored format. */
private val CORRUPT_BOOK_1 = preferencesOf(
    stringPreferencesKey("position.book-1") to "not json",
    stringPreferencesKey("position.book-2") to """{"cfi":"epubcfi(/6/4)","percent":40,"clientUpdatedAt":1700000000}""",
)

class DefaultReadingPositionStoreTest : DescribeSpec({

    fun sut(dataStore: DataStore<Preferences> = TestPreferencesDataStore()) = DefaultReadingPositionStore(dataStore)

    beforeSpec { mockAndroidLog() }

    afterSpec { unmockAndroidLog() }

    describe("reading a stored position") {

        context("when a position was saved") {
            it("TC-15: reads it back, with or without its nullable fields") {
                checkAll(
                    Exhaustive.of(
                        randomReadingPosition().copy(cfi = randomString(), percent = 40),
                        randomReadingPosition().copy(cfi = randomString(), percent = null),
                        randomReadingPosition().copy(cfi = null, percent = 57),
                    ),
                ) { position ->
                    val store = sut()

                    store.save(position)

                    store.getPositionFlow(position.bookUuid).first() shouldBe position
                }
            }
        }

        context("when a book was saved twice") {
            it("TC-15: reads the last write") {
                val store = sut()
                val first = randomReadingPosition()
                val last = randomReadingPosition().copy(bookUuid = first.bookUuid)
                store.save(first)

                store.save(last)

                store.getPositionFlow(first.bookUuid).first() shouldBe last
            }
        }

        context("when only another book was saved") {
            it("TC-17: reads null") {
                val store = sut()
                val saved = randomReadingPosition()

                store.save(saved)

                store.getPositionFlow(saved.bookUuid + "-other").first().shouldBeNull()
            }
        }

        context("when another book is saved later") {
            it("TC-17: leaves this book's position") {
                val store = sut()
                val saved = randomReadingPosition()
                store.save(saved)

                store.save(randomReadingPosition().copy(bookUuid = saved.bookUuid + "-other"))

                store.getPositionFlow(saved.bookUuid).first() shouldBe saved
            }
        }

        context("when a stored value is corrupt") {
            it("TC-19: reads null for that book") {
                sut(TestPreferencesDataStore(CORRUPT_BOOK_1)).getPositionFlow("book-1").first().shouldBeNull()
            }

            it("TC-19: still reads other books") {
                sut(TestPreferencesDataStore(CORRUPT_BOOK_1)).getPositionFlow("book-2").first() shouldBe
                    ReadingPosition("book-2", "epubcfi(/6/4)", 40, 1_700_000_000)
            }
        }

        context("when a stored value has unknown fields") {
            it("TC-19: ignores them") {
                val stored = preferencesOf(
                    stringPreferencesKey("position.book-1") to """{"percent":40,"clientUpdatedAt":1700000000,"foo":1}""",
                )

                sut(TestPreferencesDataStore(stored)).getPositionFlow("book-1").first() shouldBe
                    ReadingPosition("book-1", null, 40, 1_700_000_000)
            }
        }

        context("when the read fails") {
            it("TC-21: emits null once") {
                sut(FailingPreferencesDataStore()).getPositionFlow(randomString()).toList() shouldBe listOf(null)
            }
        }
    }

    describe("saving a position") {

        context("when the write fails") {
            it("TC-21: does not throw") {
                shouldNotThrowAny { sut(FailingPreferencesDataStore()).save(randomReadingPosition()) }
            }
        }
    }
})
