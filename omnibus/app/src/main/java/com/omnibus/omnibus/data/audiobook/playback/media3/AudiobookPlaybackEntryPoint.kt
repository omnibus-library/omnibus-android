package com.omnibus.omnibus.data.audiobook.playback.media3

import com.omnibus.omnibus.data.audiobook.playback.AudioEngine
import com.omnibus.omnibus.data.network.OmnibusHttpClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Lets instrumented tests reach the playback singletons the service and engine share. Production code injects them
 * directly.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AudiobookPlaybackEntryPoint {
    fun playbackServiceBridge(): PlaybackServiceBridge
    fun audioEngine(): AudioEngine
    fun audiobookPlayerFactory(): AudiobookPlayerFactory
    fun omnibusHttpClient(): OmnibusHttpClient
}
