package com.omnibus.omnibus.data.libraries.upgrade

/** The DataStore files of the builds before libraries, which the upgrade reads and then deletes. */
internal object LegacyFiles {

    const val READING_POSITIONS = "reading_positions.preferences_pb"
    const val AUDIO_POSITIONS = "audiobook_positions.preferences_pb"
    const val PLAYBACK_RATES = "audiobook_playback_rates.preferences_pb"
    const val RESUME_OFFERS = "audiobook_resume_offers.preferences_pb"
    const val SERVER_CONFIGURATION = "server_configuration.preferences_pb"

    /** The per-book files. They are deleted before [SERVER_CONFIGURATION], which is deleted last. */
    val PER_BOOK = listOf(READING_POSITIONS, AUDIO_POSITIONS, PLAYBACK_RATES, RESUME_OFFERS)
}
