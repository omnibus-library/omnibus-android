package com.omnibus.omnibus.testing

import io.kotest.property.RandomSource
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * The seed behind every random fixture. It changes per run and is printed to the test report. To replay a
 * failure, find `TEST_SEED=` in the report and hard-code that value here.
 */
val TEST_SEED: Long = Random.nextLong().also { println("TEST_SEED=$it") }

val TEST_RANDOM: Random = Random(TEST_SEED)

val TEST_RANDOM_SOURCE: RandomSource = RandomSource.seeded(TEST_SEED)

private const val ALPHANUMERIC = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

fun randomBoolean(): Boolean = TEST_RANDOM.nextBoolean()

fun randomInt(range: IntRange = 0..1_000_000): Int = TEST_RANDOM.nextInt(range.first, range.last + 1)

fun randomLong(range: LongRange = 0L..1_000_000_000_000L): Long = TEST_RANDOM.nextLong(range.first, range.last + 1)

fun randomDouble(from: Double = 0.0, until: Double = 1.0): Double = TEST_RANDOM.nextDouble(from, until)

fun randomString(length: IntRange = 1..16): String =
    String(CharArray(randomInt(length)) { ALPHANUMERIC[TEST_RANDOM.nextInt(ALPHANUMERIC.length)] })

fun randomDuration(range: LongRange = 0L..86_400_000L): Duration = randomLong(range).milliseconds

fun <T> randomOf(values: Collection<T>): T = values.random(TEST_RANDOM)

inline fun <reified T : Enum<T>> randomEnum(): T = randomOf(enumValues<T>().toList())
