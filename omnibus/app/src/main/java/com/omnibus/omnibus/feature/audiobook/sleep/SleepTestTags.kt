package com.omnibus.omnibus.feature.audiobook.sleep

/** Test tags on the sleep sheet's and the pill's nodes. */
internal object SleepTestTags {
    const val SHEET = "sleep_sheet"
    const val TITLE = "sleep_title"
    const val HEADER_COUNTDOWN = "sleep_header_countdown"
    const val OPTIONS = "sleep_options"
    const val OFF = "sleep_option_off"
    const val END_OF_CHAPTER = "sleep_option_end_of_chapter"
    const val PILL = "sleep_pill"
    const val PILL_MOON = "sleep_pill_moon"

    /** The tag of the timed cell for [minutes]. */
    fun timed(minutes: Int) = "sleep_option_$minutes"

    /** The tag of [choice]'s cell. */
    fun of(choice: SleepChoice): String = when (choice) {
        SleepChoice.Off -> OFF
        is SleepChoice.Timed -> timed(choice.minutes)
        is SleepChoice.EndOfChapter -> END_OF_CHAPTER
    }
}
