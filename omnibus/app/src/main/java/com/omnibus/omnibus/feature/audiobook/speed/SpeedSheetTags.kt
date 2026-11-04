package com.omnibus.omnibus.feature.audiobook.speed

/** Test tags on the speed sheet's nodes (#168). Presets are tagged by their index in [SPEED_PRESETS]. */
internal object SpeedSheetTags {
    const val BODY = "speed_sheet_body"
    const val TITLE = "speed_title"
    const val HEADER_RATE = "speed_current_rate"
    const val FINE_TUNE = "speed_fine_tune"
    const val RANGE = "speed_range"
    const val SLIDER = "speed_slider"
    const val SLOWER = "speed_slower"
    const val FASTER = "speed_faster"
    const val STEP_RATE = "speed_step_rate"
    const val STEP_CAPTION = "speed_step_caption"

    /** The preset cell at [index] in [SPEED_PRESETS]. */
    fun preset(index: Int) = "speed_preset_$index"
}
